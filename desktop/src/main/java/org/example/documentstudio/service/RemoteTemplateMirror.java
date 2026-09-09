package org.example.documentstudio.service;

import org.example.api.authority.ServerResourceClient;
import org.example.config.ConfigManager;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Local working cache backed by the authoritative company server in SHARED_CLIENT mode. */
final class RemoteTemplateMirror {
    private static final List<String> SERVER_FILES = List.of("template.json", "source.xlsx");

    private RemoteTemplateMirror(){}

    static void refresh(String type,Path root)throws IOException{
        if(!ConfigManager.isSharedClient())return;
        try{
            Files.createDirectories(root);
            var api=new ServerResourceClient();
            Set<String> serverKeys=new HashSet<>();
            for(var meta:api.list(type)){
                serverKeys.add(meta.key());
                Path folder=root.resolve(meta.key()).normalize();
                if(!folder.startsWith(root))continue;
                Path marker=folder.resolve(".server.sha256");
                String local=Files.isRegularFile(marker)?Files.readString(marker).trim():"";
                if(!local.equals(meta.checksum())){
                    overlayCurrentTemplate(folder,api.get(type,meta.key()));
                    Files.writeString(marker,meta.checksum());
                }
            }
            try(var stream=Files.list(root)){
                for(Path folder:stream.filter(Files::isDirectory).toList())
                    if(!serverKeys.contains(folder.getFileName().toString()))deleteTree(folder);
            }
        }catch(RuntimeException e){
            throw new IOException("Company server templates could not be refreshed",e);
        }
    }

    static void publish(String type,String key,Path folder)throws IOException{
        if(!ConfigManager.isSharedClient())return;
        try{
            byte[] zip=zipCurrentTemplate(folder);
            Path marker=folder.resolve(".server.sha256");
            String expected=Files.isRegularFile(marker)?Files.readString(marker).trim():"";
            var saved=new ServerResourceClient().put(type,key,key+".zip",zip,expected);
            Files.writeString(marker,saved.checksum());
        }catch(RuntimeException e){
            throw new IOException("Template could not be saved to the company server",e);
        }
    }

    static void delete(String type,String key)throws IOException{
        if(!ConfigManager.isSharedClient())return;
        try{new ServerResourceClient().delete(type,key);}
        catch(RuntimeException e){throw new IOException("Template could not be removed from the company server",e);}
    }

    /**
     * Only the current workbook and metadata are server-authoritative. Local version history is
     * intentionally excluded so every save does not re-upload an ever-growing archive.
     */
    private static byte[] zipCurrentTemplate(Path root)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream out=new ZipOutputStream(bytes)){
            for(String name:SERVER_FILES){
                Path p=root.resolve(name);
                if(!Files.isRegularFile(p))continue;
                ZipEntry e=new ZipEntry(name);
                e.setTime(0L);
                out.putNextEntry(e);
                Files.copy(p,out);
                out.closeEntry();
            }
        }
        if(bytes.size()==0)throw new IOException("Excel template package contains no current template files");
        return bytes.toByteArray();
    }

    /**
     * Refresh current server-owned files without deleting local history. This prevents a server
     * refresh from erasing the workstation's previous workbook versions.
     */
    private static void overlayCurrentTemplate(Path folder,byte[] zip)throws IOException{
        Files.createDirectories(folder);
        Path temp=Files.createTempDirectory(folder.getParent(),"server-template-");
        try{
            Set<String> received=new HashSet<>();
            try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){
                for(ZipEntry e;(e=in.getNextEntry())!=null;){
                    Path target=temp.resolve(e.getName()).normalize();
                    if(!target.startsWith(temp))throw new IOException("Unsafe template archive");
                    if(e.isDirectory()){Files.createDirectories(target);continue;}
                    String relative=temp.relativize(target).toString().replace('\\','/');
                    if(!SERVER_FILES.contains(relative))continue;
                    received.add(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if(!received.containsAll(SERVER_FILES))
                throw new IOException("Server Excel template package is incomplete: " + received);
            for(String name:SERVER_FILES){
                Path incoming=temp.resolve(name);
                if(received.contains(name)&&Files.isRegularFile(incoming)){
                    Files.move(incoming,folder.resolve(name),StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.createDirectories(folder.resolve("history"));
        }finally{deleteTree(temp);}
    }

    private static void deleteTree(Path p)throws IOException{
        if(!Files.exists(p))return;
        try(var walk=Files.walk(p)){
            for(Path x:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(x);
        }
    }
}
