package org.example.update;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ChecksumVerifier {
    private ChecksumVerifier() {}
    public static String sha256(Path file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(file)){ byte[] buffer=new byte[1024*128]; int read; while((read=in.read(buffer))>=0) if(read>0) digest.update(buffer,0,read); }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void verify(Path file, String expected) throws Exception {
        if(expected==null||expected.isBlank()) throw new IllegalArgumentException("The release does not provide a SHA-256 checksum for " + file.getFileName());
        String actual=sha256(file); if(!actual.equalsIgnoreCase(expected.trim())) throw new SecurityException("Update checksum verification failed. Expected " + expected + " but received " + actual + ".");
    }
}
