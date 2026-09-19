package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.config.WorkspaceManager;
import org.example.util.UiTaskExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="dse.runtime.rollback.probe", matches="true")
class SafeRollbackRuntimeProbeTest {
    @TempDir Path temp;
    private static volatile boolean toolkitStarted;

    @Test
    void screenLoadsWithoutWaitingForLargeInstallerHash() throws Exception {
        Path workspace=temp.resolve("workspace"); Files.createDirectories(workspace);
        try(AutoCloseable ignored= WorkspaceTestSupport.useTransientWorkspace(workspace)){
            ConfigManager.load();
            Path pkg= WorkspaceManager.getUpdatesFolder().resolve("Rollback/Packages/DSE-ERP-10.0.15-Windows-x64.exe");
            Files.createDirectories(pkg.getParent());
            try(RandomAccessFile raf=new RandomAccessFile(pkg.toFile(),"rw")){raf.setLength(128L*1024L*1024L);}
            ensureToolkit();
            CountDownLatch done=new CountDownLatch(1); AtomicReference<Throwable> error=new AtomicReference<>(); AtomicLong elapsed=new AtomicLong();
            Platform.runLater(()->{try{long start=System.nanoTime(); Parent root=FXMLLoader.load(org.example.util.ResourceLocator.require("/fxml/pages/SafeRollback.fxml")); assertNotNull(root); elapsed.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));}catch(Throwable t){error.set(t);}finally{done.countDown();}});
            assertTrue(done.await(10,TimeUnit.SECONDS));
            if(error.get()!=null)throw new AssertionError(error.get());
            UiTaskExecutor.cancel("safe-rollback-refresh");
            System.out.println("SAFE_ROLLBACK_OPEN largePackageMB=128 fxmlLoad_ms="+elapsed.get());
            assertTrue(elapsed.get()<1500,"Safe Rollback screen must paint before checksum work completes");
        }
    }
    private static synchronized void ensureToolkit() throws Exception {if(toolkitStarted)return;CountDownLatch l=new CountDownLatch(1);Platform.startup(l::countDown);assertTrue(l.await(10,TimeUnit.SECONDS));toolkitStarted=true;}
}
