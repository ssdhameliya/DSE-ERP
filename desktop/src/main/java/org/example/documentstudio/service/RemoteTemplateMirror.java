package org.example.documentstudio.service;

import org.example.api.authority.ServerResourceClient;
import org.example.config.ConfigManager;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Local working cache backed by the authoritative company server in SHARED_CLIENT mode. */
final class RemoteTemplateMirror {
    private RemoteTemplateMirror(){}
    static void refresh(String type,Path root)throws IOException{
        if(!ConfigManager.isSharedClient())return;
        try{
            Files.createDirectories(root);var api=new ServerResourceClient();Set<String> serverKeys=new HashSet<>();
            for(var meta:api.list(type)){serverKeys.add(meta.key());Path folder=root.resolve(meta.key()).normalize();if(!folder.startsWith(root))continue;Path marker=folder.resolve(".server.sha256");String local=Files.isRegularFile(marker)?Files.readString(marker).trim():"";if(!local.equals(meta.checksum())){replaceFolder(folder,api.get(type,meta.key()));Files.writeString(marker,meta.checksum());}}
            try(var stream=Files.list(root)){for(Path folder:stream.filter(Files::isDirectory).toList())if(!serverKeys.contains(folder.getFileName().toString()))deleteTree(folder);}
        }catch(RuntimeException e){throw new IOException("Company server templates could not be refreshed",e);}
    }
    static void publish(String type,String key,Path folder)throws IOException{
        if(!ConfigManager.isSharedClient())return;
        try{byte[] zip=zip(folder);Path marker=folder.resolve(".server.sha256");String expected=Files.isRegularFile(marker)?Files.readString(marker).trim():"";new ServerResourceClient().put(type,key,key+".zip",zip,expected);Files.writeString(marker,sha256(zip));}catch(RuntimeException e){throw new IOException("Template could not be saved to the company server",e);}
    }
    static void delete(String type,String key)throws IOException{if(!ConfigManager.isSharedClient())return;try{new ServerResourceClient().delete(type,key);}catch(RuntimeException e){throw new IOException("Template could not be removed from the company server",e);}}
    private static byte[] zip(Path root)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes);var walk=Files.walk(root)){for(Path p:walk.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals(".server.sha256")).toList()){ZipEntry e=new ZipEntry(root.relativize(p).toString().replace('\\','/'));out.putNextEntry(e);Files.copy(p,out);out.closeEntry();}}return bytes.toByteArray();}
    private static void replaceFolder(Path folder,byte[] zip)throws IOException{Path parent=folder.getParent(),temp=Files.createTempDirectory(parent,"server-template-");try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){for(ZipEntry e;(e=in.getNextEntry())!=null;){Path target=temp.resolve(e.getName()).normalize();if(!target.startsWith(temp))throw new IOException("Unsafe template archive");if(e.isDirectory())Files.createDirectories(target);else{Files.createDirectories(target.getParent());Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);}}}deleteTree(folder);Files.move(temp,folder,StandardCopyOption.REPLACE_EXISTING);}
    private static void deleteTree(Path p)throws IOException{if(!Files.exists(p))return;try(var walk=Files.walk(p)){for(Path x:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(x);}}
    private static String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
}
