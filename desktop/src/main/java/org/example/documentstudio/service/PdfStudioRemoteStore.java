package org.example.documentstudio.service;

import org.example.config.ConfigManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Synchronizes PDF Studio 3 working/published/active snapshots with the company server. */
final class PdfStudioRemoteStore {
    private static final int MAX_ZIP_ENTRIES = 2000;
    private static final long MAX_SINGLE_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024 * 1024;
    private PdfStudioRemoteStore() {}

    static void refresh(Path root) throws IOException {
        if (!ConfigManager.isSharedClient()) return;
        try {
            Files.createDirectories(root);
            PdfStudioServerClient api = new PdfStudioServerClient();
            Set<String> serverKeys = new HashSet<>();
            for (var meta : api.list()) {
                serverKeys.add(meta.key());
                Path folder = root.resolve(meta.key()).normalize();
                if (!folder.startsWith(root)) continue;
                Path marker = folder.resolve(".server.sha256");
                String local = Files.isRegularFile(marker) ? Files.readString(marker).trim() : "";
                if (!local.equals(meta.checksum())) {
                    replaceFolder(folder, api.get(meta.key()));
                    Files.writeString(marker, meta.checksum());
                }
            }
            try (var stream = Files.list(root)) {
                for (Path folder : stream.filter(Files::isDirectory).toList())
                    if (!serverKeys.contains(folder.getFileName().toString())) deleteTree(folder);
            }
        } catch (RuntimeException error) {
            throw new IOException("Company server PDF Studio templates could not be refreshed", error);
        }
    }

    static void publish(String key, Path folder) throws IOException {
        if (!ConfigManager.isSharedClient()) return;
        try {
            byte[] zip = zip(folder);
            Path marker = folder.resolve(".server.sha256");
            String expected = Files.isRegularFile(marker) ? Files.readString(marker).trim() : "";
            new PdfStudioServerClient().put(key, key + ".zip", zip, expected);
            Files.writeString(marker, sha256(zip));
        } catch (RuntimeException error) {
            throw new IOException("PDF Studio template could not be saved to the company server", error);
        }
    }

    static void delete(String key) throws IOException {
        if (!ConfigManager.isSharedClient()) return;
        try { new PdfStudioServerClient().delete(key); }
        catch (RuntimeException error) { throw new IOException("PDF Studio template could not be removed from the company server", error); }
    }

    private static byte[] zip(Path root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes); var walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals(".server.sha256")).toList()) {
                ZipEntry entry = new ZipEntry(root.relativize(path).toString().replace('\\', '/'));
                out.putNextEntry(entry); Files.copy(path, out); out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void replaceFolder(Path folder, byte[] zip) throws IOException {
        Path parent = folder.getParent();
        Path temp = Files.createTempDirectory(parent, "pdf-studio-server-");
        boolean moved = false;
        int entries=0; long total=0; Set<String> names=new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null;) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IOException("PDF Studio archive contains too many entries");
                String name=entry.getName().replace('\\','/');
                if (!names.add(name)) throw new IOException("PDF Studio archive contains duplicate entry: "+name);
                if (name.startsWith("/") || name.matches("^[A-Za-z]:/.*") || java.util.Arrays.asList(name.split("/")).contains(".."))
                    throw new IOException("Unsafe PDF Studio template archive path: "+name);
                Path target = temp.resolve(name).normalize();
                if (!target.startsWith(temp)) throw new IOException("Unsafe PDF Studio template archive");
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    try (var out=Files.newOutputStream(target)) { byte[] buffer=new byte[16*1024]; long entryTotal=0; for(int read;(read=in.read(buffer))>=0;){if(read==0)continue;entryTotal+=read;total+=read;if(entryTotal>MAX_SINGLE_ENTRY_BYTES||total>MAX_EXPANDED_BYTES)throw new IOException("PDF Studio archive expands beyond the allowed size");out.write(buffer,0,read);} }
                }
            }
            deleteTree(folder);
            Files.move(temp, folder, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally { if (!moved) deleteTree(temp); }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    private static String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
