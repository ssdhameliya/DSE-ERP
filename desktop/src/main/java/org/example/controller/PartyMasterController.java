package org.example.controller;

import org.example.util.OwnedAlert;


import org.example.util.IconFactory;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.model.Party;
import org.example.service.ItemSpreadsheetService;
import org.example.service.NotificationService;
import org.example.service.PartyService;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;
import org.example.util.UiTaskExecutor;
import org.example.navigation.NavigationManager;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;

public abstract class PartyMasterController {
    @FXML
    protected TextField txtSearch;
    @FXML
    protected TableView<Party> tableParties;
    @FXML
    protected TableColumn<Party, String> colCode, colName, colContact, colPhone, colEmail, colGstin, colAddress;
    @FXML
    protected TableColumn<Party, Double> colOpeningBalance;
    @FXML
    protected TableColumn<Party, Boolean> colActive;
    @FXML
    protected TableColumn<Party, Void> colActions;
    @FXML
    protected Label lblRecordCount;
    @FXML protected Label lblKpiTotal,lblKpiActive,lblKpiGst,lblKpiBalance;
    @FXML protected StackPane kpiTotalIcon,kpiActiveIcon,kpiGstIcon,kpiBalanceIcon;
    @FXML protected StackPane partyPageIcon;
    private final PartyService service = new PartyService();

    private final List<Party> cachedParties = new java.util.ArrayList<>();

    protected abstract String partyType();

    protected abstract String displayName();

    @FXML
    public void initialize() {
        String partySemantic="CUSTOMER".equals(partyType())?"customer":"supplier";
        if (partyPageIcon != null) partyPageIcon.getChildren().setAll(IconFactory.icon(partySemantic, 24));
        if(kpiTotalIcon!=null)kpiTotalIcon.getChildren().setAll(IconFactory.icon(partySemantic,20));
        if(kpiActiveIcon!=null)kpiActiveIcon.getChildren().setAll(IconFactory.icon("complete",20));
        if(kpiGstIcon!=null)kpiGstIcon.getChildren().setAll(IconFactory.icon("tax",20));
        if(kpiBalanceIcon!=null)kpiBalanceIcon.getChildren().setAll(IconFactory.icon("balance",20));
        configureExplicitTableHeaderIcons();
        colCode.setCellValueFactory(new PropertyValueFactory<>("partyCode"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contactPerson"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colGstin.setCellValueFactory(new PropertyValueFactory<>("gstin"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colOpeningBalance.setCellValueFactory(new PropertyValueFactory<>("openingBalance"));
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));
        configureActionColumn();
        tableParties.setFixedCellSize(40);
        configureTableInteractions();
        txtSearch.textProperty().addListener((o, oldValue, newValue) -> applyLocalFilter());
        org.example.util.OperationalUiSupport.focusSearch(txtSearch);
        load();
    }


    /** Standard row-level Actions menu used consistently across customer and supplier masters. */
    private void configureActionColumn() {
        if (colActions == null) return;
        colActions.setCellFactory(column -> new TableCell<>() {
            private final MenuButton actions = new MenuButton("Actions");
            private final MenuItem edit = new MenuItem("Edit " + displayName(), IconFactory.compactIcon("edit", 16));
            private final MenuItem delete = new MenuItem("Delete " + displayName(), IconFactory.compactIcon("delete", 16));
            {
                actions.getStyleClass().addAll("row-actions", "table-action-menu");
                actions.getProperties().put("erp.icon.skip", true);
                actions.setGraphic(IconFactory.compactIcon("actions", 16));
                actions.setContentDisplay(ContentDisplay.LEFT);
                actions.setGraphicTextGap(6);
                
                actions.setTooltip(new Tooltip("Actions"));
                edit.setOnAction(event -> runForRow(this, false));
                delete.getStyleClass().add("danger-menu-item");
                delete.setOnAction(event -> runForRow(this, true));
                actions.getItems().addAll(edit, new SeparatorMenuItem(), delete);
                IconFactory.decorateActionMenu(actions);
                setAlignment(javafx.geometry.Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actions);
            }
        });
    }

    private void runForRow(TableCell<Party, Void> cell, boolean delete) {
        int index = cell.getIndex();
        if (index < 0 || index >= tableParties.getItems().size()) return;
        Party party = tableParties.getItems().get(index);
        tableParties.getSelectionModel().select(party);
        tableParties.scrollTo(party);
        if (delete) deleteParty(); else editParty();
    }

    /** Enables double-click editing and a row-specific Add/Edit/Delete context menu. */
    private void configureTableInteractions() {
        tableParties.setRowFactory(table -> {
            TableRow<Party> row = new TableRow<>();
            MenuItem edit = new MenuItem("Edit " + displayName());
            MenuItem delete = new MenuItem("Delete " + displayName());
            edit.setGraphic(IconFactory.compactIcon("edit",15));
            delete.setGraphic(IconFactory.compactIcon("delete",15));
            ContextMenu menu = new ContextMenu(edit, delete);
            IconFactory.decorateActionMenu(menu);
            edit.setOnAction(event -> { selectRow(row); editParty(); });
            delete.setOnAction(event -> { selectRow(row); deleteParty(); });

            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) { menu.hide(); return; }
                selectRow(row);
                menu.show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    selectRow(row);
                    editParty();
                    event.consume();
                }
            });
            return row;
        });
    }

    private void selectRow(TableRow<Party> row) {
        tableParties.getSelectionModel().select(row.getItem());
        tableParties.requestFocus();
    }

    @FXML
    protected void newParty() {
        open(null);
    }

    @FXML
    protected void editParty() {
        Party party = tableParties.getSelectionModel().getSelectedItem();
        if (party == null) {
            warning("Select a " + displayName().toLowerCase() + " to edit.");
            return;
        }
        open(party);
    }


    @FXML
    protected void deleteParty() {
        Party party = tableParties.getSelectionModel().getSelectedItem();
        if (party == null) {
            warning("Select a " + displayName().toLowerCase() + " to delete.");
            return;
        }
        Alert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION, "Delete '" + party.getName() + "'? This cannot be undone.", ButtonType.YES, ButtonType.NO);
        confirmation.setHeaderText("Confirm deletion");
        if (confirmation.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            UiTaskExecutor.submitAction(
                "party-delete-" + party.getId(),
                () -> { service.delete(party); return null; },
                ignored -> {
                    NotificationService.createNotification(
                        displayName() + " deleted",
                        party.getPartyCode() + " - " + party.getName(),
                        "WARN",
                        partyType().equals("CUSTOMER") ? "/fxml/pages/Customer.fxml" : "/fxml/pages/Suppliers.fxml",
                        party.getPartyCode());
                    load();
                },
                failure -> warning(message(failure))
            );
        }
    }

    @FXML
    protected void refresh() {
        load();
    }

    /** Opens the shared import workspace with Customer or Supplier preselected. */
    @FXML
    protected void importParties() {
        ImportScreenContext.select(partyType().equals("CUSTOMER") ? "Customer" : "Supplier");
        NavigationManager.getInstance().loadPage("/fxml/pages/Import.fxml");
    }

    private void open(Party party) {
        try {
            FXMLLoader loader = new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/PartyDialog.fxml"));
            Parent root = loader.load(); org.example.util.ProfessionalUiEnhancer.enhance(root);
            PartyDialogController controller = loader.getController();
            controller.configure(partyType(), party);
            Stage stage = new Stage();
            PlatformUiSupport.configureDialogStage(stage, tableParties, (party == null ? "Add " : "Edit ") + displayName(), false);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene);
            stage.setScene(scene);
            stage.showAndWait();
            load();
        } catch (Exception exception) {
            error("Could not open the dialog: " + exception.getMessage());
        }
    }

    private void load() {
        org.example.util.OperationalUiSupport.showLoading(tableParties, "Loading " + displayName().toLowerCase(Locale.ROOT) + "s…");
        UiTaskExecutor.submitLatest(
            "party-master-load-" + partyType().toLowerCase(Locale.ROOT),
            () -> service.getByType(partyType()),
            loaded -> {
                List<Party> all = loaded == null ? List.of() : List.copyOf(loaded);
                cachedParties.clear();
                cachedParties.addAll(all);
                applyLocalFilter();
                if(lblKpiTotal!=null)lblKpiTotal.setText(String.valueOf(all.size()));
                if(lblKpiActive!=null)lblKpiActive.setText(String.valueOf(all.stream().filter(Party::isActive).count()));
                if(lblKpiGst!=null)lblKpiGst.setText(String.valueOf(all.stream().filter(p->p.getGstin()!=null&&!p.getGstin().isBlank()).count()));
                if(lblKpiBalance!=null)lblKpiBalance.setText(String.format(Locale.ENGLISH,"₹ %,.2f",all.stream().mapToDouble(Party::getOpeningBalance).sum()));
            },
            failure -> {
                org.example.util.OperationalUiSupport.showError(tableParties, displayName() + " master could not load", failure);
                error("Could not load " + displayName().toLowerCase(Locale.ROOT) + "s: " + message(failure));
            }
        );
    }

    /** Filters the cached customer/supplier master without a network request per key press. */
    private void applyLocalFilter() {
        String query = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        tableParties.getItems().setAll(cachedParties.stream().filter(p -> query.isEmpty()
            || (p.getPartyCode()!=null && p.getPartyCode().toLowerCase(Locale.ROOT).contains(query))
            || (p.getName()!=null && p.getName().toLowerCase(Locale.ROOT).contains(query))
            || (p.getPhone() != null && p.getPhone().contains(query))).toList());
        int count = tableParties.getItems().size();
        if(count==0)org.example.util.OperationalUiSupport.showEmpty(tableParties,"No "+displayName().toLowerCase(Locale.ROOT)+"s found","Add a "+displayName().toLowerCase(Locale.ROOT)+" or update the current search.");
        lblRecordCount.setText("Showing " + count + " Record" + (count == 1 ? "" : "s"));
    }

    private String message(Throwable failure) { return failure == null ? "Unknown error" : (failure.getMessage() == null || failure.getMessage().isBlank() ? failure.toString() : failure.getMessage()); }

    private void warning(String message) {
        Alert alert = new OwnedAlert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void error(String message) {
        Alert alert = new OwnedAlert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @FXML
    protected void exportparties() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export " + displayName() + "s");
        chooser.setInitialFileName(displayName().toLowerCase(Locale.ROOT) + "-master.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File file = chooser.showSaveDialog(tableParties.getScene().getWindow());
        if (file == null) return;

        Path path = file.toPath();
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            path = Path.of(path + ".xlsx");
        }

        try {
            // Reuse ItemSpreadsheetService for writing Excel
            ItemSpreadsheetService spreadsheetService = new ItemSpreadsheetService();
            spreadsheetService.exportparties(service.getByType(partyType()), path);

            org.example.util.ToastManager.success(tableParties, "Export complete",
                displayName() + " master exported to:\n" + path);
        } catch (Exception ex) {
            error("Could not export the workbook: " + ex.getMessage());
        }
    }



    private void configureExplicitTableHeaderIcons() {
        String partySemantic = "CUSTOMER".equals(partyType()) ? "customer" : "supplier";
        IconFactory.applyTableHeaderIcon(colCode, "identity");
        IconFactory.applyTableHeaderIcon(colName, partySemantic);
        IconFactory.applyTableHeaderIcon(colContact, "user");
        IconFactory.applyTableHeaderIcon(colPhone, "phone");
        IconFactory.applyTableHeaderIcon(colEmail, "email");
        IconFactory.applyTableHeaderIcon(colGstin, "tax");
        IconFactory.applyTableHeaderIcon(colAddress, "location");
        IconFactory.applyTableHeaderIcon(colOpeningBalance, "balance");
        IconFactory.applyTableHeaderIcon(colActive, "status");
        if (colActions != null) IconFactory.applyTableHeaderIcon(colActions, "actions");
    }
}
