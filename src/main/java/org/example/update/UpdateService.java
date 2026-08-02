package org.example.update;

import org.example.backup.BackupManager;
import org.example.config.ConfigManager;
import java.awt.Desktop;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.DoubleConsumer;

public final class UpdateService {
    public static final String DEFAULT_VERSION="2.0.0";
    public static final String DEFAULT_GITHUB_OWNER="ssdhameliya";
    public static final String DEFAULT_GITHUB_REPOSITORY="DSE-ERP";
    private final GitHubReleaseClient releases=new GitHubReleaseClient();
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();

    public String currentVersion(){return BuildInfo.version();}
    public UpdateRelease check() throws Exception {
        String owner=ConfigManager.get("update.github.owner",DEFAULT_GITHUB_OWNER).trim(); String repo=ConfigManager.get("update.github.repository",DEFAULT_GITHUB_REPOSITORY).trim();
        boolean beta="BETA".equalsIgnoreCase(ConfigManager.get("update.channel","STABLE"));
        return releases.latest(owner,repo,beta);
    }
    public boolean isNewer(UpdateRelease release){return release.version().compareTo(SemanticVersion.parse(currentVersion()))>0;}
    public UpdateRelease.Asset assetFor(UpdateRelease release){return PlatformPackage.select(release).orElseThrow(()->new IllegalStateException("This release does not contain an installer for "+PlatformPackage.current()+"."));}

    public Path download(UpdateRelease.Asset asset, DoubleConsumer progress) throws Exception {
        Path folder=ConfigManager.getConfigFolder().resolve("Updates"); Files.createDirectories(folder);
        Path target=folder.resolve(asset.name()); Path partial=folder.resolve(asset.name()+".part"); Files.deleteIfExists(partial);
        HttpRequest request=HttpRequest.newBuilder(asset.downloadUrl()).timeout(Duration.ofMinutes(30)).header("User-Agent","DSE-ERP-Updater").GET().build();
        HttpResponse<java.io.InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Update download failed with HTTP "+response.statusCode()+".");
        long total=response.headers().firstValueAsLong("Content-Length").orElse(asset.size()); long copied=0;
        try(var in=response.body();var out=Files.newOutputStream(partial,StandardOpenOption.CREATE_NEW)){byte[] b=new byte[131072];int n;while((n=in.read(b))>=0){if(n==0)continue;out.write(b,0,n);copied+=n;if(total>0)progress.accept(Math.min(1d,(double)copied/total));}}
        Files.move(partial,target,StandardCopyOption.REPLACE_EXISTING); progress.accept(1d); return target;
    }

    public String expectedChecksum(UpdateRelease release,String assetName) throws Exception {
        Optional<UpdateRelease.Asset> checksumAsset=release.assets().stream().filter(a->{String n=a.name().toLowerCase(Locale.ROOT);return n.equals("checksums.txt")||n.equals("sha256sums.txt")||n.endsWith("-checksums.txt");}).findFirst();
        if(checksumAsset.isEmpty())return "";
        HttpRequest request=HttpRequest.newBuilder(checksumAsset.get().downloadUrl()).timeout(Duration.ofSeconds(30)).header("User-Agent","DSE-ERP-Updater").GET().build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()<200||response.statusCode()>=300)return "";
        for(String line:response.body().split("\\R")){String clean=line.trim();if(clean.isBlank()||clean.startsWith("#"))continue;String[] p=clean.split("\\s+",2);if(p.length==2&&p[1].replace("*","").trim().equals(assetName))return p[0];}
        return "";
    }

    public Path createPreUpdateBackup() throws Exception {return BackupManager.createBackup("Before-Update","PRE_UPDATE");}

    public void launchInstaller(Path installer) throws Exception {
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        if(os.contains("win")){new ProcessBuilder("cmd","/c","start","\"DSE ERP Update\"",installer.toAbsolutePath().toString()).start();return;}
        if(os.contains("mac")){new ProcessBuilder("open",installer.toAbsolutePath().toString()).start();return;}
        if(Desktop.isDesktopSupported())Desktop.getDesktop().open(installer.toFile());else throw new IllegalStateException("Automatic installer launch is not supported on this operating system.");
    }

    public void openRelease(UpdateRelease release) throws Exception {if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(release.htmlUrl());}
    public Path verifyOfflinePackage(Path packageFile,String checksum) throws Exception {if(packageFile==null||!Files.isRegularFile(packageFile))throw new IllegalArgumentException("Select a valid update package.");if(checksum!=null&&!checksum.isBlank())ChecksumVerifier.verify(packageFile,checksum);return packageFile;}
}
