package org.example.controller;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.api.recon.PurchaseReconApiClient;
import org.example.service.PermissionService;
import org.example.navigation.ScreenLifecycle;
import org.example.util.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class ReconSupplierController implements ScreenLifecycle {

    @FXML private StackPane pageIcon, kpiTotalIcon, kpiActiveIcon, kpiImportIcon, kpiReconIcon, searchIcon;
    @FXML private Label kpiTotal, kpiActive, kpiImported, kpiRecons, lblEditorTitle, lblReference;
    @FXML private TextField txtSearch, txtReference, txtName, txtGstin, txtPan, txtContact, txtPhone, txtEmail;
    @FXML private TextArea txtNotes;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private Button btnNew, btnSave, btnRefresh, btnSearch, btnReset, btnExportExcel;
    @FXML private Button btnPrevPage, btnNextPage;
    @FXML private Label lblShowing, lblPage;
    @FXML private ComboBox<Integer> cmbPageSize;
    @FXML private VBox editorPanel;
    @FXML private HBox workspaceRow;
    @FXML private TableView<PurchaseReconApiClient.SupplierDto> table;
    @FXML private TableColumn<PurchaseReconApiClient.SupplierDto, String> colRef, colName, colGstin, colPhone, colSource, colStatus;
    @FXML private TableColumn<PurchaseReconApiClient.SupplierDto, Number> colCount;
    @FXML private TableColumn<PurchaseReconApiClient.SupplierDto, Void> colAction;

    private final PurchaseReconApiClient api = new PurchaseReconApiClient();
    private Dialog<ButtonType> editorDialog;
    private final ObservableList<PurchaseReconApiClient.SupplierDto> all = FXCollections.observableArrayList();
    private PurchaseReconApiClient.SupplierDto editing;
    private PurchaseReconApiClient.SupplierDto detailRow;
    private RegisterDetailDrawer detailDrawer;
    private Integer pendingSelectSupplierId;

    private int currentPage = 0;
    private int pageSize = 25;

    @FXML
    private void initialize() {
        pageIcon.getChildren().setAll(IconFactory.icon("supplier", 24));
        kpiTotalIcon.getChildren().setAll(IconFactory.icon("supplier", 18));
        kpiActiveIcon.getChildren().setAll(IconFactory.icon("complete", 18));
        kpiImportIcon.getChildren().setAll(IconFactory.icon("import", 18));
        kpiReconIcon.getChildren().setAll(IconFactory.icon("reconcile", 18));

        if (searchIcon != null) searchIcon.getChildren().setAll(IconFactory.compactIcon("search", 14));
        if (btnRefresh != null) btnRefresh.setGraphic(IconFactory.compactIcon("refresh", 14));
        if (btnNew != null) btnNew.setGraphic(IconFactory.compactIcon("add", 14));
        if (btnSave != null) btnSave.setGraphic(IconFactory.compactIcon("save", 14));
        if (btnSearch != null) btnSearch.setGraphic(IconFactory.compactIcon("search", 14));
        if (btnReset != null) btnReset.setGraphic(IconFactory.compactIcon("refresh", 14));
        if (btnExportExcel != null) btnExportExcel.setGraphic(IconFactory.compactIcon("excel", 14));
        if (btnPrevPage != null) btnPrevPage.setGraphic(IconFactory.compactIcon("left", 12));
        if (btnNextPage != null) btnNextPage.setGraphic(IconFactory.compactIcon("right", 12));

        cmbStatus.getItems().setAll("ACTIVE", "INACTIVE");
        cmbStatus.setValue("ACTIVE");

        if (cmbPageSize != null) {
            cmbPageSize.getItems().setAll(25, 50, 100, 200);
            cmbPageSize.setValue(25);
            cmbPageSize.valueProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && newV > 0) {
                    pageSize = newV;
                    currentPage = 0;
                    updatePageSlice();
                }
            });
        }

        configureTable();
        btnNew.setDisable(!PermissionService.allowed("RECON_SUPPLIER.CREATE"));
        if (workspaceRow != null && editorPanel != null) {
            workspaceRow.getChildren().remove(editorPanel);
            editorPanel.setManaged(true);
            editorPanel.setVisible(true);
        }
        installDetailDrawer();
        RealtimeSearchSupport.installRemote(txtSearch, this::applySearch);
        org.example.util.OperationalUiSupport.focusWorkArea(table);
        refresh();
    }

    private void configureTable() {
        colRef.setCellValueFactory(v -> new SimpleStringProperty(s(v.getValue().reference())));
        colName.setCellValueFactory(v -> new SimpleStringProperty(s(v.getValue().legalName())));
        colGstin.setCellValueFactory(v -> new SimpleStringProperty(s(v.getValue().gstin())));
        colPhone.setCellValueFactory(v -> new SimpleStringProperty(s(v.getValue().phone())));
        colCount.setCellValueFactory(v -> new SimpleLongProperty(v.getValue().reconCount()));

        // Make Recon Records count interactive with drill-down dialog
        colCount.setCellFactory(col -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();
            {
                link.getStyleClass().addAll("table-link", "bold-label");
                link.setOnAction(e -> {
                    PurchaseReconApiClient.SupplierDto row = getTableView().getItems().get(getIndex());
                    if (row != null) {
                        openRecordsDialog(row);
                    }
                });
            }
            @Override
            protected void updateItem(Number val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setGraphic(null);
                } else {
                    link.setText(String.valueOf(val.longValue()));
                    if (val.longValue() > 0) {
                        link.setGraphic(IconFactory.compactIcon("reconcile", 12));
                        link.setTooltip(new Tooltip("Click to view " + val.longValue() + " linked Purchase Recon records"));
                    } else {
                        link.setGraphic(null);
                        link.setTooltip(new Tooltip("No linked records"));
                    }
                    setGraphic(link);
                }
            }
        });

        colSource.setCellValueFactory(v -> new SimpleStringProperty(s(v.getValue().source())));
        colStatus.setCellValueFactory(v -> new SimpleStringProperty(s(v.getValue().status())));
        colStatus.setCellFactory(x -> SemanticTableCells.status("status"));

        colAction.setCellFactory(c -> new TableCell<>() {
            private final MenuButton actions = new MenuButton("Actions");
            private PurchaseReconApiClient.SupplierDto row;
            {
                actions.setGraphic(IconFactory.compactIcon("actions", 14));
                actions.getStyleClass().addAll("table-action-menu", "approved-row-action");
                actions.setOnShowing(e -> rebuild());
                IconFactory.decorateActionMenu(actions);
            }
            private void rebuild() {
                actions.getItems().clear();
                if (row == null) return;
                MenuItem view = new MenuItem("View", IconFactory.compactIcon("view", 14));
                view.setOnAction(e -> {
                    table.getSelectionModel().select(row);
                    showDetails(row);
                });
                MenuItem viewRecords = new MenuItem("View Recon Records (" + row.reconCount() + ")", IconFactory.compactIcon("reconcile", 14));
                viewRecords.setOnAction(e -> openRecordsDialog(row));

                MenuItem edit = new MenuItem("Edit", IconFactory.compactIcon("edit", 14));
                edit.setDisable(!PermissionService.allowed("RECON_SUPPLIER.EDIT"));
                edit.setOnAction(e -> edit(row));

                MenuItem del = new MenuItem("Delete", IconFactory.compactIcon("delete", 14));
                del.getStyleClass().add("danger-menu-item");
                del.setDisable(!PermissionService.allowed("RECON_SUPPLIER.DELETE") || row.reconCount() > 0);
                del.setOnAction(e -> deleteSupplier(row));

                actions.getItems().setAll(view, viewRecords, edit, del);
            }
            @Override
            protected void updateItem(Void x, boolean empty) {
                super.updateItem(x, empty);
                row = empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size() ? null : getTableView().getItems().get(getIndex());
                setGraphic(row == null ? null : actions);
            }
        });

        table.setRowFactory(tv -> {
            TableRow<PurchaseReconApiClient.SupplierDto> r = new TableRow<>();
            r.setOnMouseClicked(e -> {
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY || e.getClickCount() != 1 || r.isEmpty() || RegisterUiSupport.isInteractiveTableTarget(e.getPickResult().getIntersectedNode(), r)) return;
                var clicked = r.getItem();
                if (detailDrawer != null && detailDrawer.isOpen() && detailRow == clicked) closeDetails();
                else {
                    table.getSelectionModel().select(clicked);
                    showDetails(clicked);
                }
                e.consume();
            });
            return r;
        });
    }

    private void openRecordsDialog(PurchaseReconApiClient.SupplierDto row) {
        if (row == null) return;
        UiTaskExecutor.submitLatest("recon-supplier-records-" + row.id(),
                () -> api.supplierRecords(row.id()),
                records -> {
                    var dialog = new ReconSupplierRecordsDialog(table.getScene().getWindow(), row, records);
                    dialog.show();
                },
                failure -> error(failure)
        );
    }

    private void installDetailDrawer() {
        detailDrawer = new RegisterDetailDrawer();
        detailDrawer.setCloseAction(this::closeDetails);
        if (workspaceRow != null) {
            workspaceRow.getChildren().add(detailDrawer);
            if (table != null && table.getParent() != null) HBox.setHgrow(table.getParent(), Priority.ALWAYS);
            OperationalUiSupport.installEscapeClose(workspaceRow, detailDrawer::isOpen, this::closeDetails);
        }
    }

    private void showDetails(PurchaseReconApiClient.SupplierDto x) {
        if (x == null || detailDrawer == null) return;
        detailRow = x;
        detailDrawer.showRecord("Recon Supplier Details", s(x.reference()) + " • " + s(x.legalName()), List.of(
                RegisterDetailDrawer.field("Reference", x.reference(), "reference"),
                RegisterDetailDrawer.field("Trade / Legal Name", x.legalName(), "supplier"),
                RegisterDetailDrawer.field("Status", x.status(), RegisterDetailDrawer.statusSemantic(x.status())),
                RegisterDetailDrawer.field("GSTIN", x.gstin(), "tax"),
                RegisterDetailDrawer.field("PAN", x.pan(), "identity"),
                RegisterDetailDrawer.field("Contact Person", x.contactPerson(), "user"),
                RegisterDetailDrawer.field("Phone", x.phone(), "phone"),
                RegisterDetailDrawer.field("Email", x.email(), "email"),
                RegisterDetailDrawer.field("Source", x.source(), "source"),
                RegisterDetailDrawer.field("Recon Records", x.reconCount(), "purchase"),
                RegisterDetailDrawer.field("Notes", x.notes(), "notes")
        ));
        Button editButton = new Button("Edit Recon Supplier");
        editButton.getStyleClass().addAll("approved-button", "approved-primary-button");
        editButton.setGraphic(IconFactory.compactIcon("edit", 14));
        editButton.setDisable(!PermissionService.allowed("RECON_SUPPLIER.EDIT"));
        editButton.setOnAction(e -> edit(x));

        Button viewRecordsBtn = new Button("View Recon Records (" + x.reconCount() + ")");
        viewRecordsBtn.getStyleClass().addAll("approved-button", "approved-secondary-button");
        viewRecordsBtn.setGraphic(IconFactory.compactIcon("reconcile", 14));
        viewRecordsBtn.setOnAction(e -> openRecordsDialog(x));

        detailDrawer.setActions(editButton, viewRecordsBtn);
    }

    private void closeDetails() {
        detailRow = null;
        if (detailDrawer != null) detailDrawer.hideDrawer();
        if (table != null) table.getSelectionModel().clearSelection();
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        OperationalUiSupport.focusWorkArea(table);
        if (reusedFromCache && ScreenRefreshPolicy.shouldRefresh("recon-supplier", ScreenRefreshPolicy.Mode.WHEN_STALE, java.time.Duration.ofSeconds(60))) {
            refresh();
        }
    }

    @FXML
    private void refresh() {
        String q = s(txtSearch.getText()).trim();
        OperationalUiSupport.showLoading(table, "Loading recon suppliers…");
        UiTaskExecutor.submitLatest("recon-supplier-load", () -> api.searchSuppliers(q, 1000), list -> {
            all.setAll(list == null ? List.of() : list);
            currentPage = 0;
            updatePageSlice();
            ScreenRefreshPolicy.markRefreshed("recon-supplier");
            updateKpis();
            if (pendingSelectSupplierId != null) {
                selectSavedSupplier(pendingSelectSupplierId);
                pendingSelectSupplierId = null;
            }
            if (all.isEmpty()) {
                OperationalUiSupport.showEmpty(table, "No recon suppliers found", "Create a Recon Supplier or update the search.");
            }
        }, failure -> {
            OperationalUiSupport.showError(table, "Recon Supplier register could not load", failure);
            error(failure);
        });
    }

    private void updatePageSlice() {
        int total = all.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
        if (currentPage >= totalPages) currentPage = Math.max(0, totalPages - 1);

        int from = currentPage * pageSize;
        int to = Math.min(from + pageSize, total);

        if (total == 0) {
            table.getItems().clear();
            if (lblShowing != null) lblShowing.setText("Showing 0 records");
            if (lblPage != null) lblPage.setText("Page 0 of 0");
            if (btnPrevPage != null) btnPrevPage.setDisable(true);
            if (btnNextPage != null) btnNextPage.setDisable(true);
            return;
        }

        table.getItems().setAll(all.subList(from, to));
        if (lblShowing != null) lblShowing.setText(String.format("Showing %d to %d of %d records", from + 1, to, total));
        if (lblPage != null) lblPage.setText(String.format("Page %d of %d", currentPage + 1, totalPages));
        if (btnPrevPage != null) btnPrevPage.setDisable(currentPage == 0);
        if (btnNextPage != null) btnNextPage.setDisable(currentPage >= totalPages - 1);
    }

    @FXML
    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updatePageSlice();
        }
    }

    @FXML
    private void nextPage() {
        int totalPages = (int) Math.ceil((double) all.size() / pageSize);
        if (currentPage < totalPages - 1) {
            currentPage++;
            updatePageSlice();
        }
    }

    @FXML
    private void resetFilters() {
        txtSearch.clear();
        currentPage = 0;
        refresh();
    }

    @FXML
    private void exportExcel() {
        if (all.isEmpty()) {
            ToastManager.info(table, "Export Excel", "No suppliers to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("Recon_Suppliers_Register.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;

        try (Workbook wb = new XSSFWorkbook(); FileOutputStream out = new FileOutputStream(file)) {
            Sheet sheet = wb.createSheet("Recon Suppliers");
            String[] headers = {"Reference", "Legal / Trade Name", "GSTIN", "PAN", "Contact Person", "Phone", "Email", "Recon Records", "Source", "Status", "Notes"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowIdx = 1;
            for (PurchaseReconApiClient.SupplierDto s : all) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(s(s.reference()));
                row.createCell(1).setCellValue(s(s.legalName()));
                row.createCell(2).setCellValue(s(s.gstin()));
                row.createCell(3).setCellValue(s(s.pan()));
                row.createCell(4).setCellValue(s(s.contactPerson()));
                row.createCell(5).setCellValue(s(s.phone()));
                row.createCell(6).setCellValue(s(s.email()));
                row.createCell(7).setCellValue(s.reconCount());
                row.createCell(8).setCellValue(s(s.source()));
                row.createCell(9).setCellValue(s(s.status()));
                row.createCell(10).setCellValue(s(s.notes()));
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            ToastManager.success(table, "Export Completed", "Exported " + all.size() + " suppliers to " + file.getName());
        } catch (Exception ex) {
            AppDialogService.error(table, "Export Failed", "Could not export suppliers to Excel", ex.getMessage());
        }
    }

    private void selectSavedSupplier(int id) {
        var row = all.stream().filter(x -> x.id() == id).findFirst().orElse(null);
        if (row == null && !s(txtSearch.getText()).isBlank()) {
            txtSearch.clear();
            pendingSelectSupplierId = id;
            refresh();
            return;
        }
        if (row != null) {
            int idx = all.indexOf(row);
            currentPage = idx / pageSize;
            updatePageSlice();
            table.getSelectionModel().select(row);
            table.scrollTo(row);
            showDetails(row);
        }
    }

    @FXML
    private void applySearch() {
        currentPage = 0;
        refresh();
    }

    private void updateKpis() {
        kpiTotal.setText(String.valueOf(all.size()));
        kpiActive.setText(String.valueOf(all.stream().filter(x -> "ACTIVE".equalsIgnoreCase(x.status())).count()));
        kpiImported.setText(String.valueOf(all.stream().filter(x -> "IMPORT".equalsIgnoreCase(x.source())).count()));
        kpiRecons.setText(String.valueOf(all.stream().mapToLong(PurchaseReconApiClient.SupplierDto::reconCount).sum()));
    }

    private void deleteSupplier(PurchaseReconApiClient.SupplierDto row) {
        if (row == null || !PermissionService.allowed("RECON_SUPPLIER.DELETE")) return;
        if (!AppDialogService.confirm(table, "Delete Recon Supplier", "Delete " + row.legalName() + "?", "Only suppliers with no Purchase Recon records can be deleted.")) return;
        UiTaskExecutor.submitAction("recon-supplier-delete-" + row.id(), () -> {
            api.deleteSupplier(row.id());
            return true;
        }, ok -> {
            ToastManager.success(table, "Recon Supplier deleted", row.legalName() + " was deleted.");
            refresh();
        }, this::error);
    }

    @FXML
    private void newSupplier() {
        editing = null;
        if (txtReference != null) {
            txtReference.setText("Generating reference…");
            txtReference.setEditable(false);
        }
        txtName.clear();
        txtGstin.clear();
        txtPan.clear();
        txtContact.clear();
        txtPhone.clear();
        txtEmail.clear();
        txtNotes.clear();
        cmbStatus.setValue("ACTIVE");
        lblEditorTitle.setText("New Recon Supplier");
        lblReference.setText("Generating reference…");
        btnSave.setDisable(!PermissionService.allowed("RECON_SUPPLIER.CREATE"));

        // Fetch auto-generated next reference
        UiTaskExecutor.submitAction("recon-supplier-next-ref",
                () -> api.nextSupplierReference(),
                nextRef -> {
                    String ref = safeRef(nextRef);
                    if (txtReference != null) txtReference.setText(ref);
                    lblReference.setText(ref);
                },
                err -> {
                    if (txtReference != null) txtReference.setText("");
                    lblReference.setText("Reference generated on save");
                }
        );

        showEditor();
    }

    private String safeRef(String ref) {
        return (ref == null || ref.isBlank()) ? "Auto-generated on save" : ref.trim();
    }

    private void edit(PurchaseReconApiClient.SupplierDto x) {
        if (x == null) return;
        editing = x;
        if (txtReference != null) {
            txtReference.setText(s(x.reference()));
            txtReference.setEditable(false);
        }
        txtName.setText(s(x.legalName()));
        txtGstin.setText(s(x.gstin()));
        txtPan.setText(s(x.pan()));
        txtContact.setText(s(x.contactPerson()));
        txtPhone.setText(s(x.phone()));
        txtEmail.setText(s(x.email()));
        txtNotes.setText(s(x.notes()));
        cmbStatus.setValue(s(x.status()).isBlank() ? "ACTIVE" : x.status());
        lblEditorTitle.setText("Edit Recon Supplier");
        lblReference.setText(s(x.reference()));
        btnSave.setDisable(!PermissionService.allowed("RECON_SUPPLIER.EDIT"));
        showEditor();
    }

    private void showEditor() {
        if (editorDialog == null) {
            editorDialog = new OwnedDialog<>();
            editorDialog.getDialogPane().setContent(editorPanel);
            editorDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        }
        editorDialog.setTitle(editing == null ? "New Recon Supplier" : "Edit Recon Supplier");
        if (!editorDialog.isShowing()) editorDialog.show();
        txtName.requestFocus();
    }

    @FXML
    private void cancelEdit() {
        if (editorDialog != null && editorDialog.isShowing()) editorDialog.close();
        editing = null;
    }

    @FXML
    private void saveSupplier() {
        if (txtName.getText() == null || txtName.getText().isBlank()) {
            AppDialogService.warning(table, "Recon Supplier", "Name required", "Enter the Trade / Legal Name.");
            return;
        }
        var req = new PurchaseReconApiClient.SupplierSaveRequest(
                editing == null ? null : editing.id(),
                txtName.getText().trim(),
                s(txtGstin.getText()).trim(),
                s(txtPan.getText()).trim(),
                s(txtContact.getText()).trim(),
                s(txtPhone.getText()).trim(),
                s(txtEmail.getText()).trim(),
                s(txtNotes.getText()).trim(),
                cmbStatus.getValue(),
                editing == null ? 0L : editing.rowVersion()
        );
        UiTaskExecutor.submitAction("recon-supplier-save", () -> api.saveSupplier(req), saved -> {
            ToastManager.success(table, "Recon Supplier saved", saved.reference() + " • " + saved.legalName());
            pendingSelectSupplierId = saved.id();
            cancelEdit();
            refresh();
        }, this::error);
    }

    private void error(Throwable e) {
        String m = e == null ? "Operation failed" : e.getMessage();
        AppDialogService.error(table, "Recon Supplier", "Operation failed", m == null ? "Operation failed" : m);
    }

    private static String s(String x) {
        return x == null ? "" : x;
    }
}
