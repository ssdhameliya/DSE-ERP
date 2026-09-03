package org.example.documentstudio.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.example.controller.DashboardController;
import org.example.documentstudio.model.*;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.example.documentstudio.service.PdfPermissionException;
import org.example.documentstudio.service.PdfTemplateRenderer;
import org.example.documentstudio.service.DocumentFlowRegistry;
import org.example.documentstudio.service.TemplateStorageService;
import org.example.documentstudio.service.ExcelTemplateStorageService;
import org.example.documentstudio.service.ExcelTemplateRenderer;
import org.example.documentstudio.util.PdfPreviewSupport;
import org.example.navigation.ScreenLifecycle;
import org.example.util.ModernDialog;
import org.example.util.IconFactory;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Universal PDF and Excel document library and entry point for DSE ERP. */
public class DocumentStudioController implements ScreenLifecycle {
    @FXML private VBox root;
    @FXML private StackPane documentStudioPageIcon;
    @FXML private StackPane documentsKpiIcon, activeKpiIcon, defaultsKpiIcon, salesSafetyKpiIcon, generalPdfModeIcon, erpTemplateModeIcon, oneEditorModeIcon;
    @FXML private FlowPane cards;
        @FXML private HBox pdfActions,excelActions,pdfEmptyActions,excelEmptyActions;
    @FXML private Label lblStudioTitle,lblStudioSubtitle,lblLibraryTitle,lblLibraryTip,lblEmptyTitle,lblEmptyText;
    @FXML private Label lblTotalKpiTitle,lblActiveKpiTitle,lblSafetyText;
    @FXML private Label lblGuideOneTitle,lblGuideOneText,lblGuideTwoTitle,lblGuideTwoText,lblGuideThreeTitle,lblGuideThreeText;
    @FXML private VBox emptyState;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<Object> cmbType;
    @FXML private ComboBox<Object> cmbStatus;
    @FXML private Label lblTotal;
    @FXML private Label lblActive;
    @FXML private Label lblPurchaseDefault;

    private List<DocumentTemplate> all = List.of();
    private List<ExcelTemplate> excelAll = List.of();
    private boolean excelMode;

    @FXML
    public void initialize() {
        excelMode = DocumentStudioContext.currentMode() == DocumentStudioContext.Mode.EXCEL;
        if(documentsKpiIcon!=null)documentsKpiIcon.getChildren().setAll(IconFactory.icon("document",18));
        if(activeKpiIcon!=null)activeKpiIcon.getChildren().setAll(IconFactory.icon("complete",18));
        if(defaultsKpiIcon!=null)defaultsKpiIcon.getChildren().setAll(IconFactory.icon("workflow",18));
        if(salesSafetyKpiIcon!=null)salesSafetyKpiIcon.getChildren().setAll(IconFactory.icon("security",18));
        applyModePresentation();
        cmbType.setItems(FXCollections.observableArrayList());
        cmbType.getItems().add("All document types");
        cmbType.getItems().addAll(Arrays.asList(DocumentType.values()));
        cmbType.getSelectionModel().selectFirst();
        cmbStatus.setItems(FXCollections.observableArrayList("All status", TemplateStatus.DRAFT,
                TemplateStatus.ACTIVE, TemplateStatus.ARCHIVED));
        cmbStatus.getSelectionModel().selectFirst();
        txtSearch.textProperty().addListener((obs, old, value) -> applyFilters());
        cmbType.valueProperty().addListener((obs, old, value) -> applyFilters());
        cmbStatus.valueProperty().addListener((obs, old, value) -> applyFilters());
        refresh();
    }

    @Override public void onScreenShown(boolean reused) {
        excelMode = DocumentStudioContext.currentMode() == DocumentStudioContext.Mode.EXCEL;
        applyModePresentation();
        refresh();
    }

    @FXML
    public void refresh() {
        all = TemplateStorageService.listAll();
        excelAll = ExcelTemplateStorageService.listAll();
        if(excelMode){
            lblTotal.setText(Integer.toString(excelAll.size()));
            lblActive.setText(Long.toString(excelAll.stream().filter(t->t.getStatus()==TemplateStatus.ACTIVE).count()));
            String sales=ExcelTemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).map(ExcelTemplate::getName).orElse("Built-in Excel");
            String purchase=ExcelTemplateStorageService.defaultFor(DocumentType.PURCHASE_INVOICE).map(ExcelTemplate::getName).orElse("Built-in Excel");
            String purchaseReturn=ExcelTemplateStorageService.defaultFor(DocumentType.PURCHASE_RETURN).map(ExcelTemplate::getName).orElse("Built-in Excel");
            String quotation=ExcelTemplateStorageService.defaultFor(DocumentType.QUOTATION).map(ExcelTemplate::getName).orElse("Built-in Excel");
            lblPurchaseDefault.setText("Excel • Sales: "+sales+"   •   Purchase: "+purchase+"   •   Purchase Return: "+purchaseReturn+"   •   Quotation: "+quotation+"   •   Missing/invalid defaults use built-in Excel.");
        }else{
            lblTotal.setText(Integer.toString(all.size()));
            lblActive.setText(Long.toString(all.stream().filter(t -> t.getStatus() == TemplateStatus.ACTIVE).count()));
            String sales = TemplateStorageService.defaultFor(DocumentType.SALES_INVOICE).map(DocumentTemplate::getName).orElse(DocumentFlowRegistry.builtInLabel(DocumentType.SALES_INVOICE));
            String purchase = TemplateStorageService.defaultFor(DocumentType.PURCHASE_INVOICE).map(DocumentTemplate::getName).orElse(DocumentFlowRegistry.builtInLabel(DocumentType.PURCHASE_INVOICE));
            String purchaseReturn = TemplateStorageService.defaultFor(DocumentType.PURCHASE_RETURN).map(DocumentTemplate::getName).orElse(DocumentFlowRegistry.builtInLabel(DocumentType.PURCHASE_RETURN));
            String quotation = TemplateStorageService.defaultFor(DocumentType.QUOTATION).map(DocumentTemplate::getName).orElse(DocumentFlowRegistry.builtInLabel(DocumentType.QUOTATION));
            lblPurchaseDefault.setText("PDF • Sales: " + sales + "   •   Purchase: " + purchase + "   •   Purchase Return: " + purchaseReturn + "   •   Quotation: " + quotation);
        }
        applyFilters();
    }

    private void applyModePresentation(){
        boolean excel=excelMode;
        if(documentStudioPageIcon!=null)documentStudioPageIcon.getChildren().setAll(IconFactory.icon(excel?"excel":"pdf",24));
        if(generalPdfModeIcon!=null)generalPdfModeIcon.getChildren().setAll(IconFactory.icon(excel?"import":"pdf",18));
        if(erpTemplateModeIcon!=null)erpTemplateModeIcon.getChildren().setAll(IconFactory.icon(excel?"workflow":"document",18));
        if(oneEditorModeIcon!=null)oneEditorModeIcon.getChildren().setAll(IconFactory.icon(excel?"excel":"edit",18));
        if(pdfActions!=null){pdfActions.setVisible(!excel);pdfActions.setManaged(!excel);}
        if(excelActions!=null){excelActions.setVisible(excel);excelActions.setManaged(excel);}
        if(pdfEmptyActions!=null){pdfEmptyActions.setVisible(!excel);pdfEmptyActions.setManaged(!excel);}
        if(excelEmptyActions!=null){excelEmptyActions.setVisible(excel);excelEmptyActions.setManaged(excel);}
        if(lblStudioTitle!=null)lblStudioTitle.setText(excel?"Excel Studio":"PDF Studio");
        if(lblStudioSubtitle!=null)lblStudioSubtitle.setText(excel?"Create, map and automate Excel business documents from one protected workspace.":"Create, map and automate PDF business documents from one protected workspace.");
        if(lblTotalKpiTitle!=null)lblTotalKpiTitle.setText(excel?"Excel Templates":"PDF Documents & Templates");
        if(lblActiveKpiTitle!=null)lblActiveKpiTitle.setText(excel?"Active Excel Templates":"Active PDF Templates");
        if(lblSafetyText!=null)lblSafetyText.setText(excel?"PDF defaults and existing document runtime remain unchanged":"Excel defaults and existing document runtime remain unchanged");
        if(lblLibraryTitle!=null)lblLibraryTitle.setText(excel?"Excel Template Library":"PDF Template Library");
        if(lblLibraryTip!=null)lblLibraryTip.setText(excel?"Excel templates are isolated from PDF templates and use a built-in workbook whenever no valid default exists.":"General PDFs and reusable ERP templates are stored together in your workspace.");
        if(lblEmptyTitle!=null)lblEmptyTitle.setText(excel?"No Excel templates yet":"No PDF templates yet");
        if(lblEmptyText!=null)lblEmptyText.setText(excel?"Upload an .xlsx workbook or create a starter ERP Excel template. Runtime Excel output already has a built-in fallback.":"Import an existing PDF, create a blank document, or start an ERP template.");
        if(lblGuideOneTitle!=null)lblGuideOneTitle.setText(excel?"UPLOAD WORKBOOK":"GENERAL PDF");
        if(lblGuideOneText!=null)lblGuideOneText.setText(excel?"Upload your own .xlsx design or create a clean ERP workbook and preserve compatible Excel formatting.":"Import any PDF, map ERP fields safely, manage pages and export a new PDF.");
        if(lblGuideTwoTitle!=null)lblGuideTwoTitle.setText(excel?"ERP FIELD MAPPING":"ERP TEMPLATE");
        if(lblGuideTwoText!=null)lblGuideTwoText.setText(excel?"Drag ERP fields into cells, define repeating item and charge rows, and keep the workbook layout you designed.":"Map Purchase, Sales, Quotation, Delivery, Receipt and Return data without redesigning the source PDF.");
        if(lblGuideThreeTitle!=null)lblGuideThreeTitle.setText(excel?"DEFAULT & FALLBACK":"MAP & PREVIEW");
        if(lblGuideThreeText!=null)lblGuideThreeText.setText(excel?"Preview live sample data, make a template default, and automatically fall back to built-in Excel when needed.":"Original, mapping and final preview stay inside the same protected PDF workflow.");
    }

    private void applyFilters() {
        if (cards == null) return;
        String search = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        Object type = cmbType.getValue(); Object status = cmbStatus.getValue();
        cards.getChildren().clear();
        int count;
        if(excelMode){
            List<ExcelTemplate> filtered=excelAll.stream().filter(t->search.isBlank()||t.getName().toLowerCase().contains(search)||t.getDocumentType().label().toLowerCase().contains(search)).filter(t->!(type instanceof DocumentType dt)||t.getDocumentType()==dt).filter(t->!(status instanceof TemplateStatus st)||t.getStatus()==st).toList();
            filtered.forEach(t->cards.getChildren().add(excelCard(t)));count=filtered.size();
        }else{
            List<DocumentTemplate> filtered = all.stream().filter(t -> search.isBlank() || t.getName().toLowerCase().contains(search) || t.getDocumentType().label().toLowerCase().contains(search) || t.getCategory().label().toLowerCase().contains(search)).filter(t -> !(type instanceof DocumentType dt) || t.getDocumentType() == dt).filter(t -> !(status instanceof TemplateStatus st) || t.getStatus() == st).toList();
            filtered.forEach(t->cards.getChildren().add(card(t)));count=filtered.size();
        }
        boolean empty=count==0;emptyState.setVisible(empty);emptyState.setManaged(empty);cards.setVisible(!empty);cards.setManaged(!empty);
    }

    @FXML private void importExcel(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "create or import Document Studio content");
        FileChooser chooser=new FileChooser();chooser.setTitle("Upload Excel Template");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook","*.xlsx"));var file=chooser.showOpenDialog(root.getScene().getWindow());if(file==null)return;
        DocumentType type=chooseExcelType();if(type==null)return;String name=askName(stripExtension(file.getName()),type.label()+" Excel");if(name==null)return;
        try{openExcelDesigner(ExcelTemplateStorageService.importWorkbook(file.toPath(),name,type));}catch(Exception e){ModernDialog.error(root,"Excel upload failed","Document Studio",rootMessage(e));}
    }
    @FXML private void createExcelTemplate(){org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "create or import Document Studio content");DocumentType type=chooseExcelType();if(type==null)return;String name=askName("New "+type.label()+" Excel",type.label()+" Excel");if(name==null)return;try{openExcelDesigner(ExcelTemplateStorageService.createBlank(name,type));}catch(Exception e){ModernDialog.error(root,"Excel template could not be created","Document Studio",rootMessage(e));}}
    private DocumentType chooseExcelType(){
        List<DocumentType> types=Arrays.stream(DocumentType.values()).filter(DocumentType::isErpConnected).toList();
        org.example.util.OwnedDialog<DocumentType> dialog=new org.example.util.OwnedDialog<>();
        dialog.setTitle("Excel Template Type");
        dialog.setHeaderText("Choose the ERP document type for this workbook");
        ComboBox<DocumentType> choice=new ComboBox<>(FXCollections.observableArrayList(types));
        choice.setPromptText("Select document type");
        choice.setMaxWidth(Double.MAX_VALUE);
        VBox content=new VBox(8,new Label("Document type:"),choice);
        dialog.getDialogPane().setContent(content);
        ButtonType create=new ButtonType("Continue",ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create,ButtonType.CANCEL);
        javafx.scene.Node createButton=dialog.getDialogPane().lookupButton(create);
        createButton.disableProperty().bind(choice.valueProperty().isNull());
        dialog.setResultConverter(button->button==create?choice.getValue():null);
        return dialog.showAndWait().orElse(null);
    }

    /** Import any PDF as General PDF or directly as any supported ERP template type. */
    @FXML
    private void importPdf() {org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "create or import Document Studio content");
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import PDF into Document Studio");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        DocumentType type = choosePdfType("Import PDF", "Choose how this PDF should be connected");
        if (type == null) return;
        String name = askName(stripExtension(file.getName()), type.label());
        if (name == null) return;
        try {
            DocumentTemplate template = importPdfWithSecurity(file.toPath(), name, type);
            if (template != null) openDesigner(template);
        } catch (Exception error) {
            ModernDialog.error(root, "Import failed", "The PDF could not be imported", rootMessage(error));
        }
    }

    @FXML
    private void createBlank() {org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "create or import Document Studio content");
        DocumentType type = choosePdfType("Blank PDF", "Choose the document type for this blank A4 template");
        if (type == null) return;
        String name = askName(type.isGeneral() ? "Untitled Document" : "New " + type.label(), type.label());
        if (name == null) return;
        try {
            openDesigner(TemplateStorageService.createBlank(name, type));
        } catch (Exception error) {
            ModernDialog.error(root, "Document could not be created", "Document Studio", rootMessage(error));
        }
    }

    private DocumentType choosePdfType(String title, String header) {
        List<DocumentType> types = Arrays.asList(DocumentType.values());
        org.example.util.OwnedChoiceDialog<DocumentType> dialog = new org.example.util.OwnedChoiceDialog<>(DocumentType.GENERAL_PDF, types);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText("Document type:");
        return dialog.showAndWait().orElse(null);
    }

    @FXML
    private void createErpTemplate() {org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "create or import Document Studio content");
        List<DocumentType> erpTypes = Arrays.stream(DocumentType.values()).filter(DocumentType::isErpConnected).toList();
        ChoiceDialog<DocumentType> typeDialog = new org.example.util.OwnedChoiceDialog<>(DocumentType.PURCHASE_INVOICE, erpTypes);
        typeDialog.setTitle("ERP Document Template");
        typeDialog.setHeaderText("Choose the ERP document type");
        typeDialog.setContentText("Document type:");
        var selectedType = typeDialog.showAndWait();
        if (selectedType.isEmpty()) return;
        DocumentType type = selectedType.get();

        ChoiceDialog<String> sourceDialog = new org.example.util.OwnedChoiceDialog<>("Import Existing PDF", "Import Existing PDF", "Start Blank A4");
        sourceDialog.setTitle("Template Source");
        sourceDialog.setHeaderText("How would you like to start " + type.label() + "?");
        sourceDialog.setContentText("Start from:");
        var source = sourceDialog.showAndWait();
        if (source.isEmpty()) return;

        try {
            if (source.get().startsWith("Import")) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Import " + type.label() + " PDF");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
                var file = chooser.showOpenDialog(root.getScene().getWindow());
                if (file == null) return;
                String name = askName(stripExtension(file.getName()), type.label());
                if (name == null) return;
                DocumentTemplate template = importPdfWithSecurity(file.toPath(), name, type);
                if (template != null) openDesigner(template);
            } else {
                String name = askName("New " + type.label(), type.label());
                if (name == null) return;
                openDesigner(TemplateStorageService.createBlank(name, type));
            }
        } catch (Exception error) {
            ModernDialog.error(root, "Template could not be created", "Document Studio", rootMessage(error));
        }
    }

    private DocumentTemplate importPdfWithSecurity(Path source, String name, DocumentType type) throws Exception {
        try {
            return TemplateStorageService.importPdf(source, name, type, "");
        } catch (InvalidPasswordException passwordRequired) {
            Optional<String> password = askPdfPassword(
                    "Protected PDF",
                    "This PDF requires a password before it can be imported.",
                    "Enter the PDF password. It is used only for this import and is never saved.");
            if (password.isEmpty()) return null;
            try {
                return TemplateStorageService.importPdf(source, name, type, password.get());
            } catch (PdfPermissionException restricted) {
                Optional<String> ownerPassword = askPdfPassword(
                        "Owner password required",
                        "The supplied password opens the PDF, but editing or text extraction is restricted.",
                        "Enter the owner password to create an editable private workspace copy.");
                if (ownerPassword.isEmpty()) return null;
                return TemplateStorageService.importPdf(source, name, type, ownerPassword.get());
            }
        } catch (PdfPermissionException restricted) {
            Optional<String> ownerPassword = askPdfPassword(
                    "Owner password required",
                    "This PDF can be viewed, but its security permissions restrict editing or text extraction.",
                    "Enter the owner password to import it as an editable Document Studio copy.");
            if (ownerPassword.isEmpty()) return null;
            return TemplateStorageService.importPdf(source, name, type, ownerPassword.get());
        }
    }

    private Optional<String> askPdfPassword(String title, String header, String message) {
        Dialog<String> dialog = new org.example.util.OwnedDialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType unlock = new ButtonType("Unlock & Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(unlock, ButtonType.CANCEL);
        PasswordField password = new PasswordField();
        password.setPromptText("PDF password");
        Label help = new Label(message);
        help.setWrapText(true);
        VBox content = new VBox(9, help, password);
        content.setPrefWidth(430);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == unlock ? password.getText() : null);
        Platform.runLater(password::requestFocus);
        return dialog.showAndWait();
    }

    private String askName(String initial, String type) {
        TextInputDialog dialog = new org.example.util.OwnedTextInputDialog(initial);
        dialog.setTitle("Template Name");
        dialog.setHeaderText("Name your " + type + " template");
        dialog.setContentText("Template name:");
        return dialog.showAndWait().map(String::trim).filter(v -> !v.isBlank()).orElse(null);
    }

    private VBox card(DocumentTemplate template) {
        VBox card = new VBox(9);
        card.getStyleClass().add("doc-template-card");
        card.setPrefWidth(320); card.setMinWidth(300); card.setMaxWidth(350);

        StackPane preview = new StackPane();
        preview.getStyleClass().add("doc-template-preview"); preview.setPrefHeight(165);
        Label loading = new Label("PDF Preview"); loading.getStyleClass().add("doc-template-preview-placeholder");
        preview.getChildren().add(loading); loadThumbnail(template, preview);

        Label name = new Label(template.getName()); name.getStyleClass().add("doc-template-name"); name.setWrapText(true);
        HBox identity = new HBox(6);
        Label category = badge(template.getCategory().label().toUpperCase(), template.getCategory() == TemplateCategory.GENERAL_PDF ? "doc-template-general" : "doc-template-erp");
        Label type = new Label(template.getDocumentType().label()); type.getStyleClass().add("doc-template-type");
        identity.getChildren().addAll(category, type);

        HBox badges = new HBox(6);
        boolean automatic = DocumentFlowRegistry.isAutomatic(template.getDocumentType());
        badges.getChildren().add(badge(template.getStatus().name(), "doc-template-status-" + template.getStatus().name().toLowerCase()));
        badges.getChildren().add(badge(automatic ? "ERP READY" : "DESIGN ONLY", automatic ? "doc-template-status-active" : "doc-template-version"));
        if (template.getPublishedVersion() > 0) badges.getChildren().add(badge("PUBLISHED v" + template.getPublishedVersion(), "doc-template-version"));
        if (automatic && template.isRuntimeEnabled() && template.isDefaultTemplate()) badges.getChildren().add(badge("★ DEFAULT v"+template.getActiveVersion(), "doc-template-default"));
        if (template.isUnpublishedChanges()) badges.getChildren().add(badge("DRAFT CHANGES", "doc-template-version"));

        HBox actions = new HBox(7);
        Button edit = new Button("Edit"); edit.setOnAction(e -> openDesigner(template));
        Button previewButton = new Button("Test Preview"); previewButton.setOnAction(e -> previewTemplate(template));
        MenuButton more = new MenuButton("Actions");
        MenuItem publish = new MenuItem("Publish Candidate"); publish.setDisable(template.getStatus()==TemplateStatus.ARCHIVED); publish.setOnAction(e -> publishTemplate(template));
        MenuItem setDefault = new MenuItem(template.isRuntimeEnabled()?"Mark Default Again":"Mark as Default");
        setDefault.setDisable(!automatic || template.getPublishedVersion()<=0 || template.isUnpublishedChanges()); setDefault.setOnAction(e -> setDefault(template));
        MenuItem duplicate = new MenuItem("Duplicate"); duplicate.setOnAction(e -> duplicate(template));
        MenuItem exportPackage = new MenuItem("Export Template + Mapping"); exportPackage.setOnAction(e -> exportTemplatePackage(template));
        MenuItem archive = new MenuItem(template.getStatus() == TemplateStatus.ARCHIVED ? "Keep Archived" : "Archive");
        archive.setDisable(template.getStatus() == TemplateStatus.ARCHIVED); archive.setOnAction(e -> archive(template));
        MenuItem delete = new MenuItem("Delete"); delete.setOnAction(e -> delete(template));
        more.getItems().addAll(publish, setDefault, duplicate, exportPackage, archive, new SeparatorMenuItem(), delete);
        edit.getStyleClass().addAll("approved-button", "approved-primary-button", "doc-template-action-button");
        previewButton.getStyleClass().addAll("approved-button", "approved-secondary-button", "doc-template-action-button");
        more.getStyleClass().addAll("approved-menu-button", "doc-template-more-button");
        edit.setGraphic(IconFactory.compactIcon("edit", 15));
        previewButton.setGraphic(IconFactory.compactIcon("view", 15));
        more.setGraphic(IconFactory.compactIcon("actions", 15));
        HBox.setHgrow(edit, Priority.ALWAYS); edit.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(previewButton, Priority.ALWAYS); previewButton.setMaxWidth(Double.MAX_VALUE);
        actions.getChildren().addAll(edit, previewButton, more);
        card.getChildren().addAll(preview, name, identity, badges, actions);
        return card;
    }

    private VBox excelCard(ExcelTemplate template){
        VBox card=new VBox(9);card.getStyleClass().add("doc-template-card");card.setPrefWidth(320);card.setMinWidth(300);card.setMaxWidth(350);
        StackPane preview=new StackPane();preview.getStyleClass().add("doc-template-preview");preview.setPrefHeight(125);VBox previewText=new VBox(7,IconFactory.icon("excel",34),new Label("EXCEL • .xlsx"));previewText.setAlignment(javafx.geometry.Pos.CENTER);preview.getChildren().add(previewText);
        Label name=new Label(template.getName());name.getStyleClass().add("doc-template-name");name.setWrapText(true);
        HBox identity=new HBox(6,badge("EXCEL","doc-template-erp"),new Label(template.getDocumentType().label()));
        boolean automatic=DocumentFlowRegistry.isExcelAutomatic(template.getDocumentType());
        HBox badges=new HBox(6,badge(template.getStatus().name(),"doc-template-status-"+template.getStatus().name().toLowerCase()),badge(automatic?"AUTOMATIC":"DESIGN ONLY",automatic?"doc-template-status-active":"doc-template-version"),badge("v"+template.getVersion(),"doc-template-version"));if(automatic&&template.isDefaultTemplate())badges.getChildren().add(badge("★ DEFAULT","doc-template-default"));
        Button edit=new Button("Edit",IconFactory.compactIcon("edit",15));edit.setOnAction(e->openExcelDesigner(template));edit.getStyleClass().addAll("approved-button","approved-primary-button","doc-template-action-button");
        Button previewButton=new Button("Preview",IconFactory.compactIcon("view",15));previewButton.getStyleClass().addAll("approved-button","approved-secondary-button","doc-template-action-button");previewButton.setOnAction(e->previewExcel(template));
        MenuButton more=new MenuButton("Actions",IconFactory.compactIcon("actions",15));MenuItem setDefault=new MenuItem("Set as Default");setDefault.setDisable(!automatic);setDefault.setOnAction(e->setExcelDefault(template));MenuItem duplicate=new MenuItem("Duplicate");duplicate.setOnAction(e->{org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "duplicate an Excel template");try{ExcelTemplateStorageService.duplicate(template);refresh();}catch(Exception ex){ModernDialog.error(root,"Could not duplicate","Excel Studio",rootMessage(ex));}});MenuItem download=new MenuItem("Download Template");download.setOnAction(e->downloadExcelTemplate(template));MenuItem archive=new MenuItem("Archive");archive.setDisable(template.getStatus()==TemplateStatus.ARCHIVED);archive.setOnAction(e->{org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "archive an Excel template");try{ExcelTemplateStorageService.archive(template);refresh();}catch(Exception ex){ModernDialog.error(root,"Could not archive","Excel Studio",rootMessage(ex));}});MenuItem delete=new MenuItem("Delete");delete.setOnAction(e->deleteExcel(template));more.getItems().addAll(setDefault,duplicate,download,archive,new SeparatorMenuItem(),delete);more.getStyleClass().addAll("approved-menu-button","doc-template-more-button");
        HBox actions=new HBox(7,edit,previewButton,more);HBox.setHgrow(edit,Priority.ALWAYS);HBox.setHgrow(previewButton,Priority.ALWAYS);edit.setMaxWidth(Double.MAX_VALUE);previewButton.setMaxWidth(Double.MAX_VALUE);card.getChildren().addAll(preview,name,identity,badges,actions);return card;
    }
    private void openExcelDesigner(ExcelTemplate template){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "edit an Excel template");ExcelStudioContext.open(template.getId());DashboardController.navigateFromDocumentStudio("Excel Studio","/fxml/pages/ExcelDesigner.fxml");}
    private void previewExcel(ExcelTemplate template){try{Path out=org.example.config.WorkspaceManager.getTempFolder().resolve("excel-studio-sample-"+template.getId()+".xlsx");ExcelTemplateRenderer.renderSample(template,out);if(Desktop.isDesktopSupported())Desktop.getDesktop().open(out.toFile());}catch(Exception e){ModernDialog.error(root,"Excel preview failed","Document Studio",rootMessage(e));}}
    private void setExcelDefault(ExcelTemplate template){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "activate a default Excel template");if(!ModernDialog.confirm(root,"Activate Default Excel Template","Use "+template.getName()+" as the default "+template.getDocumentType().label()+" Excel template?","If this workbook becomes unavailable or invalid, DSE ERP automatically falls back to its built-in Excel output."))return;try{ExcelTemplateStorageService.activateAndSetDefault(template);ModernDialog.success(root,"Default Excel template updated",template.getName()+" is now active. Built-in Excel remains the automatic fallback.");refresh();}catch(Exception e){ModernDialog.error(root,"Could not set Excel default","Document Studio",rootMessage(e));}}
    private void downloadExcelTemplate(ExcelTemplate template){org.example.service.PermissionService.require("DOCUMENT_STUDIO.MANAGE_TEMPLATES", "download an Excel template");try{FileChooser chooser=new FileChooser();chooser.setTitle("Download Excel Template");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook","*.xlsx"));chooser.setInitialFileName(template.getName().replaceAll("[^A-Za-z0-9._ -]","_")+".xlsx");var file=chooser.showSaveDialog(root.getScene().getWindow());if(file!=null)java.nio.file.Files.copy(ExcelTemplateStorageService.sourceWorkbook(template),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){ModernDialog.error(root,"Excel download failed","Document Studio",rootMessage(e));}}
    private void deleteExcel(ExcelTemplate template){org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "delete an Excel template");if(template.isDefaultTemplate()){ModernDialog.info(root,"Default template","Excel Studio","Choose another default or archive this template first. Runtime Excel will otherwise use its built-in fallback.");return;}if(!ModernDialog.confirm(root,"Delete Excel Template","Delete "+template.getName()+"?","This removes the Document Studio copy and its version history."))return;try{ExcelTemplateStorageService.delete(template);refresh();}catch(Exception e){ModernDialog.error(root,"Could not delete","Excel Studio",rootMessage(e));}}

    private void loadThumbnail(DocumentTemplate template, StackPane preview) {
        CompletableFuture.supplyAsync(() -> {
            try { return PdfPreviewSupport.renderPage(TemplateStorageService.sourcePdf(template), 0, 66); }
            catch (Exception ignored) { return null; }
        }).thenAccept(image -> {
            if (image == null) return;
            Platform.runLater(() -> {
                ImageView view = new ImageView(image); view.setPreserveRatio(true); view.setFitHeight(153); view.setFitWidth(280);
                view.getStyleClass().add("doc-template-thumbnail"); preview.getChildren().setAll(view);
            });
        });
    }

    private Label badge(String text, String style) {
        Label label = new Label(text); label.getStyleClass().addAll("doc-template-badge", style); return label;
    }

    @FXML
    private void importTemplatePackage() {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "import a PDF Studio template package");
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import DSE PDF Template");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DSE PDF Template", "*.dsetemplate"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            DocumentTemplate imported = TemplateStorageService.importPackage(file.toPath());
            refresh();
            ModernDialog.success(root, "Template imported", imported.getName() + " was imported with its PDF, mappings and assets. It is a draft until you publish it.");
        } catch (Exception error) {
            ModernDialog.error(root, "Template import failed", "PDF Studio", rootMessage(error));
        }
    }

    private void exportTemplatePackage(DocumentTemplate template) {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.MANAGE_TEMPLATES", "export a PDF Studio template package");
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PDF Template + Mapping");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("DSE PDF Template", "*.dsetemplate"));
        String safeName = template.getName().replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        chooser.setInitialFileName((safeName.isBlank() ? "DSE-PDF-Template" : safeName) + ".dsetemplate");
        var file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        Path output = file.toPath();
        if (!output.getFileName().toString().toLowerCase().endsWith(".dsetemplate"))
            output = output.resolveSibling(output.getFileName() + ".dsetemplate");
        try {
            TemplateStorageService.exportPackage(template, output);
            ModernDialog.success(root, "Template exported", "PDF + complete mapping + assets exported to:\n" + output);
        } catch (Exception error) {
            ModernDialog.error(root, "Template export failed", "PDF Studio", rootMessage(error));
        }
    }

    private void openDesigner(DocumentTemplate template) {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "edit a PDF template");
        DocumentStudioContext.open(template.getId());
        DashboardController.navigateFromDocumentStudio("PDF Studio", "/fxml/pages/PdfDesigner.fxml");
    }

    private void publishTemplate(DocumentTemplate template) {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "publish a PDF template");
        try {
            TemplateStorageService.publish(template);
            ModernDialog.success(root,"Template published",template.getName()+" is a validated candidate. Current PDF/Print/Preview/Email generation is unchanged until Mark as Default is used.");
            refresh();
        } catch (Exception error) { ModernDialog.error(root,"Could not publish","PDF Studio",rootMessage(error)); }
    }

    private void setDefault(DocumentTemplate template) {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "activate a default PDF template");
        if (!DocumentFlowRegistry.isAutomatic(template.getDocumentType())) {
            ModernDialog.info(root, "Design-only template", "No automatic ERP binding",
                    template.getDocumentType().label() + " can be designed and previewed, but it is not connected to a live automatic document flow yet.");
            return;
        }
        if(template.getPublishedVersion()<=0 || template.isUnpublishedChanges()){
            ModernDialog.info(root,"Publish required","PDF Studio","Publish the current design first. Draft and published-candidate work cannot affect existing document generation.");
            return;
        }
        String fallback = DocumentFlowRegistry.builtInLabel(template.getDocumentType());
        if (!ModernDialog.confirm(root, "Mark as System Default",
                "Activate " + template.getName() + " v"+template.getPublishedVersion()+" for " + template.getDocumentType().label() + "?",
                "This is the ONLY PDF Studio action that changes runtime routing. A separate active snapshot will be created. Later edits and publishing remain isolated until Mark as Default is used again. " + fallback + " remains the safety fallback.")) return;
        try {
            TemplateStorageService.setDefault(template.getId());
            ModernDialog.success(root, "System default activated", template.getName() + " is now the active default for " + template.getDocumentType().label() + ".");
            refresh();
        } catch (Exception error) { ModernDialog.error(root, "Could not set default", "Document Studio", rootMessage(error)); }
    }

    private void duplicate(DocumentTemplate template) {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.CREATE", "duplicate a PDF template");
        try { DocumentTemplate copy = TemplateStorageService.duplicate(template); refresh(); ModernDialog.success(root, "Document duplicated", copy.getName() + " is ready to edit."); }
        catch (Exception error) { ModernDialog.error(root, "Could not duplicate", "Document Studio", rootMessage(error)); }
    }

    private void archive(DocumentTemplate template) {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "archive a PDF template");
        if (!ModernDialog.confirm(root, "Archive Document", "Archive " + template.getName() + "?", "It remains in the library history but cannot be used as the default.")) return;
        try { TemplateStorageService.archive(template); refresh(); }
        catch (Exception error) { ModernDialog.error(root, "Could not archive", "Document Studio", rootMessage(error)); }
    }

    private void delete(DocumentTemplate template) {
        org.example.service.PermissionService.require("DOCUMENT_STUDIO.EDIT", "delete a PDF template");
        if (!ModernDialog.confirm(root, "Delete Document", "Delete " + template.getName() + "?", "This removes the workspace copy, designer metadata and assets. The original PDF you imported outside DSE ERP is not touched.")) return;
        try { TemplateStorageService.delete(template); refresh(); }
        catch (Exception error) { ModernDialog.error(root, "Could not delete", "Document Studio", rootMessage(error)); }
    }

    private void previewTemplate(DocumentTemplate template) {
        try {
            Path output = org.example.config.WorkspaceManager.getTempFolder().resolve("document-studio-sample-" + template.getId() + ".pdf");
            PdfTemplateRenderer.renderSample(template, output);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(output.toFile());
            else ModernDialog.info(root, "Preview generated", "PDF ready", output.toString());
        } catch (Exception error) { ModernDialog.error(root, "Preview failed", "The document could not be rendered", rootMessage(error)); }
    }

    private static String stripExtension(String value) { int dot = value.lastIndexOf('.'); return dot > 0 ? value.substring(0, dot) : value; }
    private static String rootMessage(Throwable error) {
        Throwable root = error; while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null || root.getMessage().isBlank() ? root.getClass().getSimpleName() : root.getMessage();
    }
}
