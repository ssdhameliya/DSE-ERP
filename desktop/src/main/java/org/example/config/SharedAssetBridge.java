package org.example.config;
import org.example.api.authority.ServerResourceClient;import java.io.IOException;import java.nio.file.*;
final class SharedAssetBridge{
 private static final String PREFIX="server-resource:";private SharedAssetBridge(){}
 static boolean isAssetKey(String k){return "company.logoPath".equals(k)||"company.signaturePath".equals(k)||"payment.qrImagePath".equals(k);}
 static String publish(String key,String value){try{var api=new ServerResourceClient();if(value==null||value.isBlank()){try{api.delete("BUSINESS_ASSET",key);}catch(Exception ignored){}return "";}Path p=Path.of(value);api.put("BUSINESS_ASSET",key,p.getFileName().toString(),Files.readAllBytes(p));return PREFIX+key;}catch(IOException e){throw new IllegalStateException("Business asset could not be uploaded to the company server",e);}}
 static String resolve(String key,String stored){if(stored==null||!stored.startsWith(PREFIX))return stored;try{Path folder=WorkspaceManager.getTempFolder().resolve("server-assets");Files.createDirectories(folder);Path target=folder.resolve(key.replaceAll("[^A-Za-z0-9._-]","_")+".asset");Files.write(target,new ServerResourceClient().get("BUSINESS_ASSET",key),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);return target.toAbsolutePath().toString();}catch(IOException e){throw new IllegalStateException("Business asset could not be downloaded from the company server",e);}}
}
