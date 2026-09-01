package org.example.update;

import org.example.backup.BackupManager;
import org.example.api.runtime.ManagedPostgresRuntime;
import org.example.api.runtime.RuntimeBootstrapper;
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
    public static final String DEFAULT_VERSION="9.0.56";
    public static final String DEFAULT_GITHUB_OWNER="ssdhameliya";
    public static final String DEFAULT_GITHUB_REPOSITORY="DSE-ERP";
    private final GitHubReleaseClient releases=new GitHubReleaseClient();
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(60)).build();

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
        Path target=folder.resolve(asset.name()); Path partial=folder.resolve(asset.name()+".part");
        if(Files.isRegularFile(target) && (asset.size()<=0 || Files.size(target)==asset.size())){progress.accept(1d);return target;}
        Files.deleteIfExists(target);
        Exception last=null;
        for(int attempt=1;attempt<=3;attempt++){
            long existing=Files.isRegularFile(partial)?Files.size(partial):0;
            HttpRequest.Builder builder=HttpRequest.newBuilder(asset.downloadUrl()).timeout(Duration.ofMinutes(45)).header("User-Agent","DSE-ERP-Updater");
            if(existing>0)builder.header("Range","bytes="+existing+"-");
            try{
                HttpResponse<java.io.InputStream> response=http.send(builder.GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                boolean resumed=existing>0&&response.statusCode()==206;
                if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Installer download failed with HTTP "+response.statusCode()+".");
                if(!resumed)existing=0;
                long total=asset.size()>0?asset.size():existing+response.headers().firstValueAsLong("Content-Length").orElse(0);
                StandardOpenOption[] options=resumed
                    ? new StandardOpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.APPEND}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING};
                long copied=existing;
                try(var in=response.body();var out=Files.newOutputStream(partial,options)){byte[] b=new byte[131072];int n;while((n=in.read(b))>=0){if(n==0)continue;out.write(b,0,n);copied+=n;if(total>0)progress.accept(Math.min(1d,(double)copied/total));}}
                if(asset.size()>0&&Files.size(partial)!=asset.size())throw new IllegalStateException("Installer download is incomplete ("+Files.size(partial)+" of "+asset.size()+" bytes).");
                Files.move(partial,target,StandardCopyOption.REPLACE_EXISTING);progress.accept(1d);return target;
            }catch(Exception failure){last=failure;if(attempt<3)pause(attempt);}
        }
        throw new IllegalStateException("Installer download failed after 3 attempts: "+message(last),last);
    }

    public String expectedChecksum(UpdateRelease release,String assetName) throws Exception {
        Optional<UpdateRelease.Asset> checksumAsset=release.assets().stream().filter(a->{String n=a.name().toLowerCase(Locale.ROOT);return n.equals("checksums.txt")||n.equals("sha256sums.txt")||n.endsWith("-checksums.txt");}).findFirst();
        if(checksumAsset.isEmpty())return "";
        Exception last=null;
        for(int attempt=1;attempt<=3;attempt++)try{
            HttpRequest request=HttpRequest.newBuilder(checksumAsset.get().downloadUrl()).timeout(Duration.ofSeconds(90)).header("User-Agent","DSE-ERP-Updater").GET().build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Checksum download failed with HTTP "+response.statusCode()+".");
            for(String line:response.body().split("\\R")){String clean=line.trim();if(clean.isBlank()||clean.startsWith("#"))continue;String[] p=clean.split("\\s+",2);if(p.length==2&&p[1].replace("*","").trim().equals(assetName))return p[0];}
            return "";
        }catch(Exception failure){last=failure;if(attempt<3)pause(attempt);}
        throw new IllegalStateException("Checksum download failed after 3 attempts. The downloaded installer was retained and will be reused: "+message(last),last);
    }

    public Path createPreUpdateBackup() throws Exception {return BackupManager.createBackup("Before-Update","PRE_UPDATE");}

    public UpdateInstallerLauncher.LaunchResult launchInstaller(Path installer, String targetVersion) throws Exception {
        // The updater must never replace runtime/postgresql while the postmaster
        // is still running from that directory. This is mandatory for managed
        // workspaces and intentionally fails closed if shutdown cannot be proven.
        RuntimeBootstrapper.shutdownManagedServer();
        ManagedPostgresRuntime.shutdownForUpdate();
        return UpdateInstallerLauncher.launch(installer, targetVersion);
    }

    /** Backwards-compatible entry point used by offline updates. */
    public UpdateInstallerLauncher.LaunchResult launchInstaller(Path installer) throws Exception {
        return launchInstaller(installer, "offline");
    }

    public void openRelease(UpdateRelease release) throws Exception {if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(release.htmlUrl());}
    public Path verifyOfflinePackage(Path packageFile,String checksum) throws Exception {if(packageFile==null||!Files.isRegularFile(packageFile))throw new IllegalArgumentException("Select a valid update package.");if(checksum!=null&&!checksum.isBlank())ChecksumVerifier.verify(packageFile,checksum);return packageFile;}
    private static void pause(int attempt) throws InterruptedException {Thread.sleep(attempt*1500L);}
    private static String message(Throwable failure){return failure==null?"Unknown network failure":(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());}
}
