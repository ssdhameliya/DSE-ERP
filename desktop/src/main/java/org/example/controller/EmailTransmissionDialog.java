package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.example.util.DialogOwnerResolver;
import org.example.util.IconFactory;
import org.example.util.OwnedDialog;
import org.example.util.UiTaskExecutor;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Enterprise Email Transmission Dialog for DSE ERP Desktop.
 * Provides complete visual inspection of recipient, subject, message body,
 * signature block, signature/stamp image, and invoice PDF preview before SMTP transmission.
 */
public class EmailTransmissionDialog {

    public record EmailDraft(
            String title,
            String subtitle,
            String documentLabel,
            String documentNo,
            String recipientEmail,
            String subject,
            String body,
            Path attachmentPdf,
            Path signatureImage
    ) {}

    public record TransmissionResult(
            String recipientEmail,
            String subject,
            String body,
            Path attachmentPdf,
            Path signatureImage
    ) {}

    @FXML private StackPane headerIconHolder;
    @FXML private Label lblTitle;
    @FXML private Label lblSubtitle;

    @FXML private TextField txtRecipient;
    @FXML private Label errRecipient;

    @FXML private TextField txtSubject;
    @FXML private Label errSubject;

    @FXML private TextArea txtBody;

    @FXML private HBox boxSignatureImage;
    @FXML private Label lblSignatureImageInfo;
    @FXML private ImageView imgSignatureThumb;

    @FXML private HBox boxAttachment;
    @FXML private StackPane attachIconHolder;
    @FXML private Label lblAttachmentName;
    @FXML private Label lblAttachmentDesc;
    @FXML private Button btnPreviewPdf;
    @FXML private Button btnPreviewSignature;

    @FXML private Button btnCancel;
    @FXML private Button btnSend;

    private EmailDraft draft;
    private Dialog<TransmissionResult> hostDialog;
    private TransmissionResult result;

    public void initialize() {
        if (headerIconHolder != null) {
            headerIconHolder.getChildren().setAll(IconFactory.icon("email", 22));
        }
        if (attachIconHolder != null) {
            attachIconHolder.getChildren().setAll(IconFactory.icon("file", 18));
        }
        if (btnPreviewPdf != null) {
            org.example.util.UiActionIcons.apply(btnPreviewPdf, "eye", "Preview PDF");
        }
        if (btnPreviewSignature != null) {
            org.example.util.UiActionIcons.apply(btnPreviewSignature, "eye", "Preview Signature");
        }
        if (btnSend != null) {
            org.example.util.UiActionIcons.apply(btnSend, "send", "Send Email");
        }
        if (txtRecipient != null) {
            txtRecipient.textProperty().addListener((obs, old, val) -> {
                if (errRecipient != null) {
                    errRecipient.setVisible(false);
                    errRecipient.setManaged(false);
                }
            });
        }
        if (txtBody != null) {
            txtBody.setMinHeight(200);
            txtBody.setPrefHeight(240);
        }
        if (txtSubject != null) {
            txtSubject.textProperty().addListener((obs, old, val) -> {
                if (errSubject != null) {
                    errSubject.setVisible(false);
                    errSubject.setManaged(false);
                }
            });
        }
    }

    public void setDraft(EmailDraft draft, Dialog<TransmissionResult> dialog) {
        this.draft = draft;
        this.hostDialog = dialog;

        if (lblTitle != null && draft.title() != null) {
            lblTitle.setText(draft.title());
        }
        if (lblSubtitle != null && draft.subtitle() != null) {
            lblSubtitle.setText(draft.subtitle());
        }
        if (txtRecipient != null) {
            txtRecipient.setText(draft.recipientEmail() == null ? "" : draft.recipientEmail().trim());
        }
        if (txtSubject != null) {
            txtSubject.setText(draft.subject() == null ? "" : draft.subject().trim());
        }
        if (txtBody != null) {
            txtBody.setText(draft.body() == null ? "" : draft.body());
            javafx.application.Platform.runLater(() -> {
                txtBody.positionCaret(0);
                txtBody.deselect();
                txtBody.requestLayout();
            });
        }

        // Signature image box
        boolean hasSignatureImage = draft.signatureImage() != null
                && Files.isRegularFile(draft.signatureImage())
                && Files.isReadable(draft.signatureImage());
        if (boxSignatureImage != null) {
            boxSignatureImage.setVisible(hasSignatureImage);
            boxSignatureImage.setManaged(hasSignatureImage);
            if (hasSignatureImage) {
                try {
                    File imgFile = draft.signatureImage().toFile();
                    if (imgSignatureThumb != null) {
                        imgSignatureThumb.setImage(new Image(imgFile.toURI().toString()));
                    }
                    if (lblSignatureImageInfo != null) {
                        lblSignatureImageInfo.setText(imgFile.getName() + " • Appended inline below signature");
                    }
                } catch (Exception ignored) {
                    boxSignatureImage.setVisible(false);
                    boxSignatureImage.setManaged(false);
                }
            }
        }

        // Attachment box
        boolean hasAttachment = draft.attachmentPdf() != null
                && Files.isRegularFile(draft.attachmentPdf())
                && Files.isReadable(draft.attachmentPdf());
        if (boxAttachment != null) {
            boxAttachment.setVisible(hasAttachment);
            boxAttachment.setManaged(hasAttachment);
            if (hasAttachment && lblAttachmentName != null) {
                lblAttachmentName.setText(draft.attachmentPdf().getFileName().toString());
            }
            if (hasAttachment && lblAttachmentDesc != null) {
                String docLabel = draft.documentLabel() == null ? "Document" : draft.documentLabel();
                lblAttachmentDesc.setText("Official " + docLabel + " attached automatically");
            }
        }

        if (btnSend != null) {
            String label = draft.documentLabel() == null ? "Email" : draft.documentLabel();
            btnSend.setText("Send " + label);
            org.example.util.UiActionIcons.apply(btnSend, "send", "Send " + label);
        }
    }

    @FXML
    private void previewPdf() {
        if (draft == null || draft.attachmentPdf() == null || !Files.isRegularFile(draft.attachmentPdf())) {
            showError("Document PDF is not yet available for preview.");
            return;
        }
        UiTaskExecutor.submitAction("preview-email-pdf", () -> {
            File pdfFile = draft.attachmentPdf().toFile();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(pdfFile);
            } else {
                new ProcessBuilder("cmd", "/c", "start", '\"' + pdfFile.getAbsolutePath() + '\"').start();
            }
            return Boolean.TRUE;
        }, done -> {}, failure -> {
            showError("Could not launch system PDF viewer:\n\n" + failure.getMessage());
        });
    }

    @FXML
    private void previewSignature() {
        Path sig = draft != null ? draft.signatureImage() : null;
        if (sig == null || !Files.isRegularFile(sig)) {
            String path = org.example.service.MessageTemplateService.getSignatureImagePath();
            if (path != null && !path.isBlank()) {
                Path p = Path.of(path);
                if (Files.isRegularFile(p)) sig = p;
            }
        }
        if (sig == null || !Files.isRegularFile(sig)) {
            // Generate standard Jasvi Industries authorized signature and stamp bitmap
            try {
                Path tempSig = Path.of(System.getProperty("java.io.tmpdir"), "jasvi-signature.png");
                java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(360, 180, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = bi.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(255, 255, 255, 0));
                g2.fillRect(0, 0, 360, 180);

                // Stamp Oval
                g2.setColor(new java.awt.Color(30, 58, 138));
                g2.setStroke(new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND, 10f, new float[]{8f, 4f}, 0f));
                g2.drawOval(15, 10, 330, 155);
                g2.setStroke(new java.awt.BasicStroke(1.2f));
                g2.drawOval(23, 18, 314, 139);

                // Stamp Header Text
                g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 15));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String header = "JASVI INDUSTRIES";
                g2.drawString(header, (360 - fm.stringWidth(header)) / 2, 45);

                g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));
                fm = g2.getFontMetrics();
                String sub = "AHMEDABAD \u2022 GUJARAT";
                g2.drawString(sub, (360 - fm.stringWidth(sub)) / 2, 60);

                // Signature strokes
                g2.setColor(new java.awt.Color(15, 23, 42));
                g2.setStroke(new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                java.awt.geom.Path2D sigPath = new java.awt.geom.Path2D.Double();
                sigPath.moveTo(80, 105);
                sigPath.curveTo(110, 70, 130, 115, 160, 95);
                sigPath.curveTo(190, 80, 220, 110, 250, 90);
                sigPath.curveTo(270, 105, 280, 95, 290, 85);
                g2.draw(sigPath);

                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawLine(100, 115, 260, 108);

                // Footer
                g2.setColor(new java.awt.Color(30, 58, 138));
                g2.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 12));
                fm = g2.getFontMetrics();
                String footer = "AUTHORIZED SIGNATORY";
                g2.drawString(footer, (360 - fm.stringWidth(footer)) / 2, 145);

                g2.dispose();
                javax.imageio.ImageIO.write(bi, "png", tempSig.toFile());
                sig = tempSig;
            } catch (Exception ignored) {}
        }

        final Path finalSig = sig;
        if (finalSig == null || !Files.isRegularFile(finalSig)) {
            showError("Company signature image is not configured or unavailable.");
            return;
        }

        // Show in-dialog preview modal with high-definition rendering
        try {
            Dialog<Void> previewDialog = new org.example.util.OwnedDialog<>();
            if (hostDialog != null && hostDialog.getOwner() != null) {
                previewDialog.initOwner(hostDialog.getOwner());
            }
            previewDialog.setTitle("Company Signature & Stamp Preview");

            VBox box = new VBox(14);
            box.setAlignment(javafx.geometry.Pos.CENTER);
            box.setPadding(new javafx.geometry.Insets(20));

            ImageView iv = new ImageView(new Image(finalSig.toUri().toString()));
            iv.setFitWidth(380);
            iv.setPreserveRatio(true);

            Label lblInfo = new Label("For, JASVI INDUSTRIES\nAuthorized Signatory & Commercial Stamp");
            lblInfo.getStyleClass().add("muted-label");
            lblInfo.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            Button btnOpenSystem = new Button("Open in System Viewer");
            btnOpenSystem.getStyleClass().addAll("approved-button", "approved-secondary-button");
            btnOpenSystem.setOnAction(e -> {
                UiTaskExecutor.submitAction("open-sys-viewer", () -> {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                        Desktop.getDesktop().open(finalSig.toFile());
                    } else {
                        new ProcessBuilder("cmd", "/c", "start", '\"' + finalSig.toFile().getAbsolutePath() + '\"').start();
                    }
                    return Boolean.TRUE;
                }, ok -> {}, err -> {});
            });

            box.getChildren().addAll(iv, lblInfo, btnOpenSystem);
            previewDialog.getDialogPane().setContent(box);
            previewDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            org.example.util.ProfessionalUiEnhancer.enhance(previewDialog.getDialogPane());
            previewDialog.show();
        } catch (Exception e) {
            UiTaskExecutor.submitAction("preview-signature-img", () -> {
                File imgFile = finalSig.toFile();
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(imgFile);
                } else {
                    new ProcessBuilder("cmd", "/c", "start", '\"' + imgFile.getAbsolutePath() + '\"').start();
                }
                return Boolean.TRUE;
            }, done -> {}, failure -> {
                showError("Could not launch signature image viewer:\n\n" + failure.getMessage());
            });
        }
    }

    @FXML
    private void send() {
        String recipient = txtRecipient == null ? "" : txtRecipient.getText().trim();
        String subject = txtSubject == null ? "" : txtSubject.getText().trim();
        String body = txtBody == null ? "" : txtBody.getText();

        if (recipient.isBlank() || !recipient.contains("@") || !recipient.contains(".")) {
            if (errRecipient != null) {
                errRecipient.setText("Please enter a valid recipient email address (e.g. accounts@example.com).");
                errRecipient.setVisible(true);
                errRecipient.setManaged(true);
            }
            if (txtRecipient != null) txtRecipient.requestFocus();
            return;
        }

        if (subject.isBlank()) {
            if (errSubject != null) {
                errSubject.setText("Subject cannot be blank.");
                errSubject.setVisible(true);
                errSubject.setManaged(true);
            }
            if (txtSubject != null) txtSubject.requestFocus();
            return;
        }

        this.result = new TransmissionResult(recipient, subject, body, draft.attachmentPdf(), draft.signatureImage());
        if (hostDialog != null) {
            hostDialog.setResult(this.result);
            hostDialog.close();
        }
    }

    @FXML
    private void cancel() {
        this.result = null;
        if (hostDialog != null) {
            hostDialog.setResult(null);
            hostDialog.close();
        }
    }

    private void showError(String message) {
        Alert alert = new org.example.util.OwnedAlert(Alert.AlertType.ERROR);
        alert.setTitle("Email Transmission");
        alert.setHeaderText("PDF Preview Notification");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static Optional<TransmissionResult> show(Node ownerNode, EmailDraft draft) {
        try {
            FXMLLoader loader = new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/EmailTransmissionDialog.fxml"));
            DialogPane pane = new DialogPane();
            BorderPane content = loader.load();
            org.example.util.ProfessionalUiEnhancer.enhance(content);

            EmailTransmissionDialog controller = loader.getController();

            Dialog<TransmissionResult> dialog = new OwnedDialog<>();
            Window owner = DialogOwnerResolver.resolve(ownerNode);
            if (owner != null) dialog.initOwner(owner);

            dialog.setTitle(draft.title() != null ? draft.title() : "Transmit Email");
            org.example.util.AppDialogRenderer.configureWorkspace(dialog, "info",
                    draft.title() != null ? draft.title() : "Transmit Email",
                    draft.title() != null ? draft.title() : "Transmit Tax Invoice via Email",
                    draft.subtitle() != null ? draft.subtitle() : "Direct SMTP delivery to customer billing contact",
                    "");
            dialog.setResizable(true);
            pane.setContent(content);
            pane.setPrefSize(860, 780);
            pane.setMinSize(760, 620);
            pane.getButtonTypes().add(ButtonType.CLOSE);
            Node closeBtn = pane.lookupButton(ButtonType.CLOSE);
            if (closeBtn != null) {
                closeBtn.setVisible(false);
                closeBtn.setManaged(false);
            }
            javafx.application.Platform.runLater(() -> {
                Node btnBar = pane.lookup(".button-bar");
                if (btnBar != null) {
                    btnBar.setVisible(false);
                    btnBar.setManaged(false);
                }
            });

            dialog.setDialogPane(pane);
            dialog.setResultConverter(buttonType -> controller.result);

            controller.setDraft(draft, dialog);

            return dialog.showAndWait();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open email transmission dialog: " + e.getMessage(), e);
        }
    }
}
