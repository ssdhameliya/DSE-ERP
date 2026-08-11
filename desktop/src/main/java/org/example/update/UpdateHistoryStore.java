package org.example.update;

import org.example.config.ConfigManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class UpdateHistoryStore {
    private static final Path FILE=ConfigManager.getConfigFolder().resolve("update-history.tsv");
    private UpdateHistoryStore() {}
    public static synchronized void append(String version,String channel,String result,String detail){
        try{ Files.createDirectories(FILE.getParent()); String safe=Objects.requireNonNullElse(detail,"").replace('\t',' ').replace('\n',' '); Files.writeString(FILE,Instant.now()+"\t"+version+"\t"+channel+"\t"+result+"\t"+safe+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND); }catch(Exception ignored){}
    }
    public static synchronized List<Entry> read(){
        if(!Files.exists(FILE))return List.of();
        try{ List<Entry> result=new ArrayList<>(); for(String line:Files.readAllLines(FILE)){String[] p=line.split("\\t",5); if(p.length>=4)result.add(new Entry(Instant.parse(p[0]),p[1],p[2],p[3],p.length==5?p[4]:""));} Collections.reverse(result); return result; }catch(Exception e){return List.of();}
    }
    public record Entry(Instant timestamp,String version,String channel,String result,String detail){}
}
