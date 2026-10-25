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
            dialog.setResizable(true);
            pane.setContent(content);
            pane.getButtonTypes().add(ButtonType.CLOSE);
            Node closeBtn = pane.lookupButton(ButtonType.CLOSE);
            if (closeBtn != null) {
                closeBtn.setVisible(false);
                closeBtn.setManaged(false);
            }

            dialog.setDialogPane(pane);
            dialog.setResultConverter(buttonType -> controller.result);

            controller.setDraft(draft, dialog);

            return dialog.showAndWait();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open email transmission dialog: " + e.getMessage(), e);
        }
    }
}
