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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.model.Party;
import org.example.service.ItemSpreadsheetService;
import org.example.service.NotificationService;
import org.example.service.PartyService;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;
import org.example.navigation.NavigationManager;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

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
    protected Label lblRecordCount;
    private final PartyService service = new PartyService();

    protected abstract String partyType();

    protected abstract String displayName();

    @FXML
    public void initialize() {
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
        tableParties.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableParties.setFixedCellSize(40);
        configureTableInteractions();
        txtSearch.textProperty().addListener((o, oldValue, newValue) -> load());
        load();
    }

    /** Enables double-click editing and a row-specific Add/Edit/Delete context menu. */
    private void configureTableInteractions() {
        tableParties.setRowFactory(table -> {
            TableRow<Party> row = new TableRow<>();
            MenuItem add = new MenuItem("＋ Add " + displayName());
            MenuItem edit = new MenuItem("✎ Edit " + displayName());
            MenuItem delete = new MenuItem("🗑 Delete " + displayName());
            ContextMenu menu = new ContextMenu(add, edit, delete);

            add.setOnAction(event -> newParty());
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
            service.delete(party.getId());
            NotificationService.createNotification(
                displayName() + " deleted",
                party.getPartyCode() + " - " + party.getName(),
                "WARN",
                partyType().equals("CUSTOMER") ? "/fxml/pages/Customer.fxml" : "/fxml/pages/Suppliers.fxml",
                party.getPartyCode());
            load();
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/PartyDialog.fxml"));
            Parent root = loader.load();
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
        String query = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        tableParties.getItems().setAll(service.getByType(partyType()).stream().filter(p -> query.isEmpty() || p.getPartyCode().toLowerCase(Locale.ROOT).contains(query) || p.getName().toLowerCase(Locale.ROOT).contains(query) || (p.getPhone() != null && p.getPhone().contains(query))).toList());
        int count = tableParties.getItems().size();
        lblRecordCount.setText("Showing " + count + " Record" + (count == 1 ? "" : "s"));
    }

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

            new OwnedAlert(Alert.AlertType.INFORMATION,
                displayName() + " master exported to:\n" + path).showAndWait();
        } catch (Exception ex) {
            error("Could not export the workbook: " + ex.getMessage());
        }
    }



    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colCode, "document");
        IconFactory.applyTableHeaderIcon(colName, "customer");
        IconFactory.applyTableHeaderIcon(colContact, "user");
        IconFactory.applyTableHeaderIcon(colPhone, "phone");
        IconFactory.applyTableHeaderIcon(colEmail, "email");
        IconFactory.applyTableHeaderIcon(colGstin, "tax");
        IconFactory.applyTableHeaderIcon(colAddress, "location");
        IconFactory.applyTableHeaderIcon(colOpeningBalance, "payment");
        IconFactory.applyTableHeaderIcon(colActive, "status");
    }
}
