package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.documentstudio.model.*;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceTestSupport;
import org.example.documentstudio.service.PdfMappingReviewSession;
import org.example.documentstudio.view.PdfMappingReviewWorkspace;
import org.example.theme.ThemeManager;
import org.example.util.AppDialogRenderer;
import org.example.util.OwnedDialog;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in runtime proof that Review Auto Mapping is rendered by the centralized DSE dialog shell. */
class PdfMappingReviewSnapshotRuntimeProbe {
    @Test
    void captureCentralizedMappingWorkspaceInLightAndDark() throws Exception {
        String folderProp = System.getProperty("dse.pdf.mapping.review.screens", "").trim();
        Assumptions.assumeTrue(!folderProp.isBlank(), "Mapping review screenshot folder was not supplied");
        Path folder = Path.of(folderProp).toAbsolutePath().normalize(); Files.createDirectories(folder);
        try (AutoCloseable ignored = WorkspaceTestSupport.useTransientWorkspace(folder.resolve("workspace"))) {
            ConfigManager.load();
            AtomicReference<Throwable> failure = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
            Runnable work = () -> {
            try {
                StackPane ownerRoot = new StackPane(); ownerRoot.setPrefSize(1360,860);
                Scene ownerScene = new Scene(ownerRoot,1360,860); ThemeManager.applyTheme(ownerScene);
                Stage owner = new Stage(); owner.setScene(ownerScene); owner.setTitle("DSE ERP - PDF Studio"); owner.show();
                capture(ownerRoot, folder.resolve("01-review-auto-mapping-light.png"));
                ThemeManager.toggle(ownerScene);
                capture(ownerRoot, folder.resolve("02-review-auto-mapping-dark.png"));
                owner.close();
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        };
            try { Platform.startup(work); } catch (IllegalStateException alreadyStarted) { Platform.runLater(work); }
            assertTrue(done.await(35, TimeUnit.SECONDS), "Mapping review screenshot timed out");
            if (failure.get()!=null) throw new AssertionError("Mapping review screenshot failed",failure.get());
            for(String name:List.of("01-review-auto-mapping-light.png","02-review-auto-mapping-dark.png")) {
                Path file=folder.resolve(name); assertTrue(Files.isRegularFile(file)); assertTrue(Files.size(file)>15_000,name+" is too small");
            }
        }
    }

    private static void capture(StackPane ownerRoot, Path output) throws Exception {
        PdfMappingReviewSession session = PdfMappingReviewSession.from(sampleTemplate());
        PdfMappingReviewWorkspace workspace = new PdfMappingReviewWorkspace(session, PdfMappingReviewSession.Section.ITEMS);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType save = new ButtonType("Save Mapping", ButtonBar.ButtonData.OK_DONE);
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(ownerRoot);
        AppDialogRenderer.configureWorkspace(dialog,"mapping","Review Auto Mapping","Review detected ERP mappings",
                "Auto detection is a suggestion layer. Confirm, change, keep static or reset mappings here; source PDF geometry stays protected.",null);
        dialog.getDialogPane().setContent(workspace);
        dialog.getDialogPane().setPrefSize(1180,760);
        dialog.getDialogPane().getButtonTypes().setAll(cancel,save);
        dialog.show();
        dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
        WritableImage image = dialog.getDialogPane().getScene().snapshot(null);
        BufferedImage buffered = new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<buffered.getHeight();y++) for(int x=0;x<buffered.getWidth();x++) buffered.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(buffered,"png",output.toFile());
        dialog.close();
    }

    private static DocumentTemplate sampleTemplate() {
        DocumentTemplate t = new DocumentTemplate(); t.setDocumentType(DocumentType.SALES_INVOICE);
        var invoice = field("INVOICE NO : IN/14-08-2026/0006","document.number",.99);
        var billing = field("H 52 Darshan Villa society, Near Gopal Chowk...","party.billingAddress",.97);
        billing.setAutoHeight(true); billing.setGrowthDirection("DOWN");
        var gstin = field("BEEPD4909N12345","party.billingGstin",.99);
        var contact = field("Shailesh Dhameliya / +91 9099839193","transport.contact",.95);
        var terms = field("(1) All Prices are Nett-Godown...","company.terms",.94);
        var table = TemplateElement.of(ElementType.ITEM_TABLE,0,25,250,540,230); table.setSourceStyleCaptured(true); table.setUseSourceTableDesign(true);
        table.setTableColumnBindings(List.of(binding("SR. NO.","item.serial",.99),binding("HSN CODE","item.hsn",.99),
                binding("PRODUCT DESCRIPTION","item.descriptionWithRemarks",.99),binding("QTY","item.quantity",.99),
                binding("UNIT RATE","item.rate",.99),binding("UNIT","item.unit",.99),binding("AMOUNT (INR)","item.taxable",.96)));
        var financial = TemplateElement.of(ElementType.BLOCK,0,390,620,175,118); financial.setReplacementGroupId("DYNAMIC_FINANCIAL_SUMMARY");
        financial.setSummaryLabelRatio(.66); financial.setGrowthDirection("UP"); financial.setAutoDetectedFieldKey("DYNAMIC_FINANCIAL_SUMMARY"); financial.setAutoDetectedConfidence(.96); financial.setMappingState("AUTO");
        t.setElements(List.of(invoice,billing,gstin,contact,table,financial,terms)); return t;
    }
    private static TemplateElement field(String source,String key,double confidence) {
        TemplateElement e=TemplateElement.of(ElementType.FIELD,0,40,40,180,22); e.setFieldKey(key); e.setText("{{"+key+"}}"); e.markAutoDetectedMapping(source,key,confidence); return e;
    }
    private static TemplateColumnBinding binding(String label,String key,double confidence) {
        TemplateColumnBinding b=new TemplateColumnBinding(label,key,10,70,"LEFT",confidence); b.setAutoDetectedFieldKey(key); b.setAutoDetectedConfidence(confidence); b.setMappingState("AUTO"); return b;
    }
}
