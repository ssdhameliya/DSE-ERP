package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.documentstudio.model.DocumentType;
import org.example.documentstudio.model.ExcelTemplate;
import org.example.documentstudio.service.ExcelTemplateStorageService;
import org.example.theme.ThemeManager;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real JavaFX screenshot used by the bundled Excel Studio mapping guide. */
class ExcelStudioGuideSnapshotRuntimeProbe {
    @Test
    void captureRealExcelStudioScreen() throws Exception {
        String outputProperty = System.getProperty("dse.excel.guide.screen", "").trim();
        org.junit.jupiter.api.Assumptions.assumeTrue(!outputProperty.isBlank(), "Guide screenshot output was not supplied");
        Path output = Path.of(outputProperty).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path workspace = output.getParent().resolve("excel-guide-snapshot-workspace");
        try (AutoCloseable ignored = WorkspaceTestSupport.useTransientWorkspace(workspace)) {
            ConfigManager.load();
            ConfigManager.setWithoutSaving("deployment.mode", "LOCAL");
            ExcelTemplate template = ExcelTemplateStorageService.createBlank("Sales Invoice - Mapping Guide", DocumentType.SALES_INVOICE);
            ExcelStudioContext.open(template.getId());

            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            Runnable work = () -> {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/ExcelDesigner.fxml"));
                    Parent root = loader.load();
                    Scene scene = new Scene(root, 1680, 980);
                    ThemeManager.applyTheme(scene);
                    Stage stage = new Stage();
                    stage.setScene(scene);
                    stage.setTitle("Excel Studio - Mapping Guide");
                    stage.show();
                    root.applyCss();
                    root.layout();
                    WritableImage image = scene.snapshot(null);
                    BufferedImage buffered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < buffered.getHeight(); y++) for (int x = 0; x < buffered.getWidth(); x++)
                        buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    ImageIO.write(buffered, "png", output.toFile());
                    stage.close();
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    done.countDown();
                }
            };
            try {
                Platform.startup(work);
            } catch (IllegalStateException alreadyStarted) {
                Platform.runLater(work);
            }
            assertTrue(done.await(25, TimeUnit.SECONDS), "Excel Studio screenshot timed out");
            if (failure.get() != null) throw new AssertionError("Excel Studio screenshot failed", failure.get());
            assertTrue(Files.isRegularFile(output));
            assertTrue(Files.size(output) > 10_000);
        }
    }
}
