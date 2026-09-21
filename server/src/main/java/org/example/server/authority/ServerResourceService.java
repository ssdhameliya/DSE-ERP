package org.example.server.authority;

import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class ServerResourceService {
    private final JpaNativeRepository db;
    public ServerResourceService(JpaNativeRepository db) { this.db = db; }


    /** API-facing operations own the transaction boundary and enforce the business permission boundary. */
    @Transactional(readOnly = true)
    public List<ResourceMeta> apiList(String type) { requireApiRead(type); return list(type); }

    @Transactional(readOnly = true)
    public ResourceFile apiGet(String type,String key) { requireApiRead(type); return get(type,key); }

    @Transactional
    public ResourceMeta apiPut(String type,String key,String fileName,String contentType,byte[] content,String expectedChecksum) {
        requireApiWrite(type); return put(type,key,fileName,contentType,content,expectedChecksum);
    }

    @Transactional
    public void apiDelete(String type,String key) { requireApiWrite(type); delete(type,key); }

    private static void requireApiRead(String type) {
        String normalized=normalize(type);
        switch(normalized){
            case "ISSUED_SALES_PDF" -> CurrentUser.requirePermission("SALES.VIEW","View issued sales PDFs");
            case "EXCEL_TEMPLATE","PDF_STUDIO_V3_TEMPLATE","PDF_STUDIO_V3_ACTIVE" -> CurrentUser.requirePermission("DOCUMENT_STUDIO.VIEW","View document templates");
            case "BUSINESS_ASSET" -> {
                if(!(CurrentUser.hasPermission("SETTINGS.VIEW")||CurrentUser.hasPermission("DOCUMENT_STUDIO.VIEW")))
                    throw new SecurityException("Viewing business assets requires SETTINGS.VIEW or DOCUMENT_STUDIO.VIEW permission");
            }
            default -> { if(!"ADMIN".equalsIgnoreCase(CurrentUser.require().role())) throw new SecurityException("Administrator permission is required for this server resource"); }
        }
    }

    private static void requireApiWrite(String type) {
        String normalized=normalize(type);
        if("ISSUED_SALES_PDF".equals(normalized))
            throw new SecurityException("Issued sales PDF snapshots are immutable through the generic resource API");
        switch(normalized){
            case "EXCEL_TEMPLATE","PDF_STUDIO_V3_TEMPLATE","PDF_STUDIO_V3_ACTIVE" -> {
                if(!(CurrentUser.hasPermission("DOCUMENT_STUDIO.EDIT")||CurrentUser.hasPermission("DOCUMENT_STUDIO.MANAGE_TEMPLATES")))
                    throw new SecurityException("Managing document templates requires DOCUMENT_STUDIO.EDIT or DOCUMENT_STUDIO.MANAGE_TEMPLATES permission");
            }
            default -> { if(!"ADMIN".equalsIgnoreCase(CurrentUser.require().role())) throw new SecurityException("Administrator permission is required for this server resource"); }
        }
    }

    @Transactional(readOnly = true)
    public List<ResourceMeta> list(String type) {
        return db.query("SELECT resource_key,COALESCE(file_name,''),COALESCE(content_type,'application/octet-stream'),checksum,updated_at,octet_length(content) FROM server_resource WHERE resource_type=? ORDER BY resource_key",
                (r,i)->new ResourceMeta(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6)), normalize(type));
    }

    @Transactional(readOnly = true)
    public ResourceFile get(String type,String key) {
        var rows=db.query("SELECT COALESCE(file_name,''),COALESCE(content_type,'application/octet-stream'),content,checksum FROM server_resource WHERE resource_type=? AND resource_key=?",
                (r,i)->new ResourceFile(r.getString(1),r.getString(2),(byte[])r.getObject(3),r.getString(4)),normalize(type),normalizeKey(key));
        if(rows.isEmpty())throw new IllegalArgumentException("Server resource not found");
        return rows.getFirst();
    }

    @Transactional
    public ResourceMeta put(String type,String key,String fileName,String contentType,byte[] content) {
        return put(type,key,fileName,contentType,content,null);
    }

    @Transactional
    public ResourceMeta put(String type,String key,String fileName,String contentType,byte[] content,String expectedChecksum) {
        if(content==null||content.length==0)throw new IllegalArgumentException("Resource content is empty");
        String normalizedType=normalize(type), normalizedKey=normalizeKey(key);
        List<String> current=db.query("SELECT checksum FROM server_resource WHERE resource_type=? AND resource_key=? FOR UPDATE",(r,i)->r.getString(1),normalizedType,normalizedKey);
        String expected=expectedChecksum==null?"":expectedChecksum.trim();
        if(!expected.isBlank()&&!current.isEmpty()&&!expected.equalsIgnoreCase(current.getFirst()))
            throw new org.example.server.web.ConcurrentEditException("Server template");
        String checksum=sha256(content), now=BusinessClock.nowUtcText();
        db.update("INSERT INTO server_resource(resource_type,resource_key,file_name,content_type,content,checksum,updated_at,updated_by) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(resource_type,resource_key) DO UPDATE SET file_name=excluded.file_name,content_type=excluded.content_type,content=excluded.content,checksum=excluded.checksum,updated_at=excluded.updated_at,updated_by=excluded.updated_by",
                normalizedType,normalizedKey,safeName(fileName),contentType==null?"application/octet-stream":contentType,content,checksum,now,CurrentUser.require().username());
        return new ResourceMeta(normalizedKey,safeName(fileName),contentType,checksum,now,content.length);
    }

    @Transactional public void delete(String type,String key){db.update("DELETE FROM server_resource WHERE resource_type=? AND resource_key=?",normalize(type),normalizeKey(key));}
    private static String normalize(String v){String n=v==null?"":v.trim().toUpperCase();if(!n.matches("[A-Z0-9_-]{1,40}"))throw new IllegalArgumentException("Invalid resource type");return n;}
    private static String normalizeKey(String v){String n=v==null?"":v.trim();if(!n.matches("[A-Za-z0-9._-]{1,240}"))throw new IllegalArgumentException("Invalid resource key");return n;}
    private static String safeName(String v){return (v==null?"resource":v).replaceAll("[^A-Za-z0-9._ -]","_");}
    private static String sha256(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception e){throw new IllegalStateException(e);}}
    public record ResourceMeta(String key,String fileName,String contentType,String checksum,String updatedAt,long size){}
    public record ResourceFile(String fileName,String contentType,byte[] content,String checksum){}
}
