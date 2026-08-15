package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedTextInputDialog;


import org.example.util.IconFactory;
import org.example.util.UiActionIcons;
import org.example.util.ButtonAction;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import org.example.model.Lookup;
import org.example.navigation.ScreenLifecycle;
import org.example.service.LookupService;
import org.example.service.MasterCategoryService;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;

import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MasterDataController implements ScreenLifecycle {

    /* =========================================================
       SIDEBAR
       ========================================================= */

    @FXML
    private ListView<String> lstTypes;

    @FXML
    private Label lblCategoryCount;

    @FXML
    private Label lblValueCount;

    /* =========================================================
       HEADER
       ========================================================= */

    @FXML
    private TextField txtSearch;

    @FXML
    private TextField txtCategorySearch;

    @FXML private StackPane categoryPanelIcon;
    @FXML private StackPane lookupValuesIcon;
    @FXML private StackPane analyticsIcon;
    @FXML private StackPane masterPageIcon;

    @FXML private Button btnHeaderRefresh;
    @FXML private Button btnHeaderAddLookup;
    @FXML private Button btnHeaderExport;
    @FXML private Button btnAddCategory;
    @FXML private Button btnRenameCategory;
    @FXML private Button btnDeleteCategory;
    @FXML private Button btnAddLookup;
    @FXML private Button btnEditLookup;
    @FXML private Button btnDeleteLookup;
    @FXML private Button btnRefreshLookup;
    @FXML private Button btnQuickAddLookup;
    @FXML private Button btnQuickAddCategory;
    @FXML private Button btnQuickRefresh;

    /* =========================================================
       DASHBOARD
       ========================================================= */

    @FXML private StackPane kpiCategoriesIcon;
    @FXML private StackPane kpiValuesIcon;
    @FXML private StackPane kpiSelectedIcon;
    @FXML private StackPane kpiStatusIcon;

    @FXML
    private Label lblDashboardCategoryCount;

    @FXML
    private Label lblDashboardValueCount;

    @FXML
    private Label lblSelectedCategory;

    /* =========================================================
       TABLE
       ========================================================= */

    @FXML
    private TableView<Lookup> tblLookup;

    @FXML
    private TableColumn<Lookup, String> colCode;

    @FXML
    private TableColumn<Lookup, String> colValue;

    @FXML
    private TableColumn<Lookup, String> colDescription;

    @FXML
    private Label lblRecordCount;

    @FXML
    private Pagination pagination;

    /* =========================================================
       ANALYTICS
       ========================================================= */

    @FXML
    private PieChart categoryPieChart;

    @FXML
    private Label lblSummaryCategoryCount;

    @FXML
    private Label lblSummaryValueCount;

    @FXML
    private Label lblSummarySelectedCategory;

    /* =========================================================
       STATUS BAR
       ========================================================= */

    @FXML
    private Label lblStatus;

    private final LookupService service = new LookupService();
    private final MasterCategoryService categoryService = new MasterCategoryService();

    private static final int PAGE_SIZE = 10;
    private List<Lookup> filteredLookups = List.of();
    private boolean updatingPagination;

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    @FXML
    public void initialize() {
        configureKpiIcons();
        configureSectionIcons();
        configureActionIcons();
        configureCategoryListCells();
        configureExplicitTableHeaderIcons();

        configureTableColumns();
        configureTableInteractions();
        configurePagination();
        configureListeners();
        configureKeyboardShortcuts();

        setStatus("Loading master data...");

        loadCategories();

        if (!lstTypes.getItems().isEmpty()) {
            lstTypes.getSelectionModel().selectFirst();
        } else {
            clearTable();
            setStatus("No master categories found.");
        }
    }



    @Override
    public void onScreenShown(boolean reusedFromCache) {
        if (!reusedFromCache) return;
        String selectedCategory = lstTypes == null ? null : lstTypes.getSelectionModel().getSelectedItem();
        loadCategories();
        if (selectedCategory != null && lstTypes.getItems().contains(selectedCategory)) {
            lstTypes.getSelectionModel().select(selectedCategory);
        }
        loadTable();
        setStatus("Master data refreshed from server.");
    }

    /**
     * Premium category navigation used only by the Master Data content page.
     * It is presentation-only and does not alter category values or database logic.
     */
    private void configureCategoryListCells() {
        if (lstTypes == null) return;

        lstTypes.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);

                if (empty || category == null || category.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String semantic = categorySemantic(category);
                Label name = new Label(category);
                name.getStyleClass().add("master-category-name");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Label state = new Label("●");
                state.getStyleClass().add("master-category-state");

                HBox content = new HBox(10, IconFactory.icon(semantic, 18), name, spacer, state);
                content.setAlignment(Pos.CENTER_LEFT);
                content.getStyleClass().add("master-category-cell-content");

                setText(null);
                setGraphic(content);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });
    }

    private String categorySemantic(String category) {
        String value = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (value.contains("BANK") || value.contains("UPI")) return "bank";
        if (value.contains("BRAND")) return "identity";
        if (value.contains("CATEGORY")) return "category";
        if (value.contains("GST") || value.contains("TAX") || value.contains("VAT")) return "tax";
        if (value.contains("MATERIAL") || value.contains("STOCK")) return "inventory";
        if (value.equals("UNIT") || value.contains("UOM") || value.contains("MEASURE")) return "unit";
        if (value.contains("DISCOUNT")) return "discount";
        if (value.contains("CURRENCY")) return "currency";
        if (value.contains("PAYMENT")) return "payment";
        if (value.contains("WAREHOUSE")) return "inventory";
        if (value.contains("TRANSPORT") || value.contains("COURIER") || value.contains("DELIVERY")) return "delivery";
        if (value.contains("STATUS") || value.contains("STATE")) return "status";
        if (value.contains("SOURCE") || value.contains("CHANNEL")) return "source";
        if (value.contains("ROLE") || value.contains("PERMISSION")) return "role";
        if (value.contains("EMAIL") || value.contains("SMTP")) return "email";
        if (value.contains("TEST")) return "test";
        return "master";
    }

    private void configureKpiIcons() {
        if (kpiCategoriesIcon != null) kpiCategoriesIcon.getChildren().setAll(IconFactory.icon("category", 24));
        if (kpiValuesIcon != null) kpiValuesIcon.getChildren().setAll(IconFactory.icon("master", 24));
        if (kpiSelectedIcon != null) kpiSelectedIcon.getChildren().setAll(IconFactory.icon("select", 24));
        if (kpiStatusIcon != null) kpiStatusIcon.getChildren().setAll(IconFactory.icon("complete", 24));
    }

    private void configureSectionIcons() {
        if (masterPageIcon != null) masterPageIcon.getChildren().setAll(IconFactory.icon("master", 24));
        if (categoryPanelIcon != null) categoryPanelIcon.getChildren().setAll(IconFactory.icon("category", 18));
        if (lookupValuesIcon != null) lookupValuesIcon.getChildren().setAll(IconFactory.icon("master", 18));
        if (analyticsIcon != null) analyticsIcon.getChildren().setAll(IconFactory.icon("analytics", 18));
    }

    private void configureActionIcons() {
        UiActionIcons.apply(btnHeaderRefresh, ButtonAction.REFRESH);
        UiActionIcons.apply(btnHeaderAddLookup, ButtonAction.ADD);
        UiActionIcons.apply(btnHeaderExport, ButtonAction.EXPORT);

        UiActionIcons.apply(btnAddCategory, ButtonAction.ADD);
        UiActionIcons.apply(btnRenameCategory, ButtonAction.EDIT);
        UiActionIcons.apply(btnDeleteCategory, ButtonAction.DELETE);

        UiActionIcons.apply(btnAddLookup, ButtonAction.ADD);
        UiActionIcons.apply(btnEditLookup, ButtonAction.EDIT);
        UiActionIcons.apply(btnDeleteLookup, ButtonAction.DELETE);
        UiActionIcons.apply(btnRefreshLookup, ButtonAction.REFRESH);

        UiActionIcons.apply(btnQuickAddLookup, ButtonAction.ADD);
        UiActionIcons.apply(btnQuickAddCategory, ButtonAction.ADD);
        UiActionIcons.apply(btnQuickRefresh, ButtonAction.REFRESH);
    }

    private void configureTableColumns() {

        colCode.setCellValueFactory(
            new PropertyValueFactory<>("lookupCode")
        );

        colValue.setCellValueFactory(
            new PropertyValueFactory<>("lookupValue")
        );

        colDescription.setCellValueFactory(
            new PropertyValueFactory<>("description")
        );

        tblLookup.setColumnResizePolicy(
            TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );
    }

    private void configureListeners() {

        lstTypes.getSelectionModel()
            .selectedItemProperty()
            .addListener((observable, oldValue, newValue) -> {

                if (newValue != null) {
                    if (kpiSelectedIcon != null) kpiSelectedIcon.getChildren().setAll(IconFactory.icon(categorySemantic(newValue), 24));
                    loadTable();
                } else {
                    clearTable();
                }
            });

        txtSearch.textProperty()
            .addListener((observable, oldValue, newValue) -> loadTable());

        if (txtCategorySearch != null) {
            txtCategorySearch.textProperty().addListener((observable, oldValue, newValue) -> filterCategories(newValue));
        }
    }

    private final List<String> allCategories = new ArrayList<>();

    private void filterCategories(String query) {
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String selected = lstTypes.getSelectionModel().getSelectedItem();
        List<String> filtered = allCategories.stream()
            .filter(category -> value.isBlank() || category.toLowerCase(Locale.ROOT).contains(value))
            .toList();
        lstTypes.setItems(FXCollections.observableArrayList(filtered));
        if (selected != null && filtered.contains(selected)) lstTypes.getSelectionModel().select(selected);
        else if (!filtered.isEmpty()) lstTypes.getSelectionModel().selectFirst();
    }

    private void configureTableInteractions() {

        tblLookup.setRowFactory(tableView -> {

            TableRow<Lookup> row = new TableRow<>();

            row.setOnMouseClicked(event -> {

                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    tblLookup.getSelectionModel().select(row.getItem());
                    editLookup();
                }
            });

            return row;
        });
    }

    private void configureKeyboardShortcuts() {

        Platform.runLater(() -> {

            Scene scene = tblLookup.getScene();

            if (scene == null) {
                return;
            }

            scene.setOnKeyPressed(event -> {

                switch (event.getCode()) {

                    case DELETE -> deleteLookup();

                    case ENTER -> {
                        if (tblLookup.getSelectionModel()
                            .getSelectedItem() != null) {

                            editLookup();
                        }
                    }

                    case F5 -> refresh();

                    case N -> {
                        if (event.isControlDown()) {
                            addLookup();
                        }
                    }

                    default -> {
                        // No action required.
                    }
                }
            });
        });
    }

    /* =========================================================
       CATEGORY LOADING
       ========================================================= */

    private void loadCategories() {
        String previouslySelected = lstTypes.getSelectionModel().getSelectedItem();
        try {
            List<MasterCategoryService.Category> rows = categoryService.getAll();
            List<String> categories = rows.stream().map(MasterCategoryService.Category::name).toList();
            allCategories.clear(); allCategories.addAll(categories);
            filterCategories(txtCategorySearch == null ? "" : txtCategorySearch.getText());
            updateCategoryCounts(categories.size());
            loadCategoryChart();
            if (previouslySelected != null && categories.contains(previouslySelected)) lstTypes.getSelectionModel().select(previouslySelected);
            else if (!categories.isEmpty()) lstTypes.getSelectionModel().selectFirst();
        } catch (Exception exception) {
            showWarning("Could not load Master Categories:\n" + exception.getMessage());
            setStatus("Failed to load categories.");
        }
    }

    private void updateCategoryCounts(int categoryCount) {

        String countText = String.valueOf(categoryCount);

        setLabelText(lblCategoryCount, countText);
        setLabelText(lblDashboardCategoryCount, countText);
        setLabelText(lblSummaryCategoryCount, countText);
    }

    /* =========================================================
       TABLE LOADING AND SEARCH
       ========================================================= */

    private void loadTable() {

        String selectedType =
            lstTypes.getSelectionModel().getSelectedItem();

        if (selectedType == null || selectedType.isBlank()) {
            clearTable();
            return;
        }

        String searchText =
            txtSearch.getText() == null
                ? ""
                : txtSearch.getText()
                .trim()
                .toLowerCase(Locale.ROOT);

        try {

            List<Lookup> lookupList =
                service.getByType(selectedType);

            if (lookupList == null) {
                lookupList = List.of();
            }

            List<Lookup> filteredList =
                lookupList.stream()
                    .filter(lookup ->
                        matchesSearch(lookup, searchText)
                    )
                    .toList();

            filteredLookups = List.copyOf(filteredList);

            updateTableDashboard(
                selectedType,
                filteredLookups.size()
            );

        } catch (Exception exception) {

            clearTable();

            showWarning(
                "Could not load lookup values:\n"
                    + exception.getMessage()
            );

            setStatus("Failed to load lookup values.");
        }
    }

    private boolean matchesSearch(
        Lookup lookup,
        String searchText
    ) {

        if (searchText == null || searchText.isBlank()) {
            return true;
        }

        if (lookup == null) {
            return false;
        }

        return containsIgnoreCase(
            lookup.getLookupCode(),
            searchText
        )
            || containsIgnoreCase(
            lookup.getLookupValue(),
            searchText
        )
            || containsIgnoreCase(
            lookup.getDescription(),
            searchText
        );
    }

    private boolean containsIgnoreCase(
        String value,
        String searchText
    ) {

        return value != null
            && value.toLowerCase(Locale.ROOT)
            .contains(searchText);
    }

    private void updateTableDashboard(
        String selectedType,
        int recordCount
    ) {

        String countText = String.valueOf(recordCount);

        setLabelText(lblValueCount, countText);
        setLabelText(lblDashboardValueCount, countText);
        setLabelText(lblSummaryValueCount, countText);

        setLabelText(lblSelectedCategory, selectedType);
        setLabelText(lblSummarySelectedCategory, selectedType);

        setLabelText(
            lblRecordCount,
            recordCount == 1
                ? "1 Record"
                : recordCount + " Records"
        );

        updatePagination();

        setStatus(
            "Loaded "
                + recordCount
                + " lookup value"
                + (recordCount == 1 ? "" : "s")
                + " for "
                + selectedType
                + "."
        );
    }

    private void clearTable() {

        filteredLookups = List.of();

        tblLookup.setItems(
            FXCollections.observableArrayList()
        );

        setLabelText(lblValueCount, "0");
        setLabelText(lblDashboardValueCount, "0");
        setLabelText(lblSummaryValueCount, "0");

        setLabelText(lblSelectedCategory, "-");
        setLabelText(lblSummarySelectedCategory, "-");

        setLabelText(lblRecordCount, "0 Records");

        updatePagination();
    }

    private void configurePagination() {
        if (pagination == null) {
            return;
        }

        pagination.getStyleClass().add("master-premium-pagination");
        pagination.setMaxPageIndicatorCount(5);
        pagination.currentPageIndexProperty().addListener((observable, oldIndex, newIndex) -> {
            if (!updatingPagination) {
                applyCurrentPage();
            }
        });
    }

    private void updatePagination() {
        if (pagination == null) {
            applyCurrentPage();
            return;
        }

        int pageCount = Math.max(1, (int) Math.ceil(filteredLookups.size() / (double) PAGE_SIZE));

        updatingPagination = true;
        try {
            pagination.setPageCount(pageCount);
            pagination.setCurrentPageIndex(0);
            pagination.setDisable(pageCount <= 1);
        } finally {
            updatingPagination = false;
        }

        applyCurrentPage();
    }

    private void applyCurrentPage() {
        int total = filteredLookups.size();
        int pageIndex = pagination == null ? 0 : pagination.getCurrentPageIndex();
        int fromIndex = Math.min(pageIndex * PAGE_SIZE, total);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        tblLookup.setItems(
            FXCollections.observableArrayList(
                filteredLookups.subList(fromIndex, toIndex)
            )
        );

        if (total == 0) {
            setLabelText(lblRecordCount, "0 Records");
        } else {
            setLabelText(
                lblRecordCount,
                "Showing " + (fromIndex + 1) + " to " + toIndex + " of " + total
                    + (total == 1 ? " Record" : " Records")
            );
        }
    }

    /* =========================================================
       PIE CHART
       ========================================================= */

    private void loadCategoryChart() {
        if (categoryPieChart == null) return;
        try {
            ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
            for (MasterCategoryService.Category row : categoryService.getAll()) {
                if (row.valueCount() > 0) chartData.add(new PieChart.Data(row.name(), row.valueCount()));
            }
            categoryPieChart.setData(chartData);
            categoryPieChart.setAnimated(false);
            categoryPieChart.setLabelsVisible(false);
            categoryPieChart.setLegendVisible(true);
            categoryPieChart.setTitle(chartData.isEmpty() ? "No Category Data" : "Category Distribution");
        } catch (Exception exception) {
            categoryPieChart.setData(FXCollections.observableArrayList());
            categoryPieChart.setTitle("Analytics Unavailable");
        }
    }

    /* =========================================================
       CATEGORY ACTIONS
       ========================================================= */

    @FXML
    private void addCategory() {
        editCategory(null);
    }

    @FXML
    private void renameCategory() {
        String selectedCategory = lstTypes.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) { showWarning("Select a Master Category to rename."); return; }
        editCategory(selectedCategory);
    }

    @FXML


    private void editCategory(String oldName) {
        boolean isNewCategory = oldName == null;
        TextInputDialog dialog = new OwnedTextInputDialog(isNewCategory ? "" : oldName);
        dialog.setTitle(isNewCategory ? "Add Master Category" : "Rename Master Category");
        dialog.setHeaderText(isNewCategory ? "Create a category for related master values" : "Rename this category and all linked values");
        dialog.setContentText("Category name:");
        dialog.showAndWait().map(String::trim).filter(value -> !value.isBlank()).ifPresent(value -> {
            String newName = value.toUpperCase(Locale.ROOT);
            try {
                if (isNewCategory) categoryService.add(newName); else categoryService.rename(oldName, newName);
                loadCategories(); lstTypes.getSelectionModel().select(newName);
                String successMessage = isNewCategory ? "Category added successfully." : "Category renamed successfully.";
                setStatus(successMessage);
                showSuccess(isNewCategory ? "Category Added" : "Category Renamed", successMessage);
            } catch (Exception exception) {
                showWarning("Master Category could not be saved. Use a unique name.\n" + exception.getMessage());
                setStatus("Category could not be saved.");
            }
        });
    }







    @FXML
    private void deleteCategory() {
        String selectedCategory = lstTypes.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) { showWarning("Select a Master Category to deactivate."); return; }
        Alert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION,
            "Deactivate category '" + selectedCategory + "' and all of its values? Existing records will remain unchanged.", ButtonType.YES, ButtonType.NO);
        confirmation.setTitle("Deactivate Master Category"); confirmation.setHeaderText("Confirm category deactivation");
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        try {
            categoryService.delete(selectedCategory); loadCategories();
            if (!lstTypes.getItems().isEmpty()) lstTypes.getSelectionModel().selectFirst(); else clearTable();
            setStatus("Category deactivated successfully.");
            showSuccess("Category Deactivated", "Master category '" + selectedCategory + "' and its values are now inactive for future use.");
        } catch (Exception exception) {
            showWarning("Category could not be deactivated:\n" + exception.getMessage()); setStatus("Category could not be deactivated.");
        }
    }





    /* =========================================================
       LOOKUP ACTIONS
       ========================================================= */

    @FXML
    private void addLookup() {

        String selectedType =
            lstTypes.getSelectionModel().getSelectedItem();

        if (selectedType == null) {

            showWarning(
                "Create or select a Master Category first."
            );

            return;
        }

        try {

            URL resource = getClass().getResource(
                "/fxml/pages/lookupDialog.fxml"
            );

            if (resource == null) {
                throw new IOException(
                    "lookupDialog.fxml was not found."
                );
            }

            FXMLLoader loader =
                new FXMLLoader(resource);

            Parent root = loader.load();

            LookupDialogController controller =
                loader.getController();

            controller.setLookupType(selectedType);

            Stage stage = createDialogStage(
                root,
                "Add Master"
            );

            stage.showAndWait();

            loadCategories();

            lstTypes.getSelectionModel()
                .select(selectedType);

            loadTable();

            if (controller.wasSaved()) {
                setStatus("Master record added successfully.");
                showSuccess("Master Record Added", "The new " + selectedType + " record was saved successfully.");
            } else {
                setStatus("Add Master cancelled.");
            }

        } catch (IOException exception) {

            exception.printStackTrace();

            showError(
                "Could not open the Add Master dialog:\n"
                    + exception.getMessage()
            );

            setStatus(
                "Could not open the Add Master dialog."
            );
        }
    }

    @FXML
    private void editLookup() {

        Lookup selectedLookup =
            tblLookup.getSelectionModel()
                .getSelectedItem();

        if (selectedLookup == null) {

            showWarning(
                "Select a master record to edit."
            );

            return;
        }

        openDialog(selectedLookup);
    }

    private void openDialog(Lookup lookup) {

        String selectedType =
            lstTypes.getSelectionModel().getSelectedItem();

        try {

            URL resource = getClass().getResource(
                "/fxml/pages/lookupDialog.fxml"
            );

            if (resource == null) {
                throw new IOException(
                    "lookupDialog.fxml was not found."
                );
            }

            FXMLLoader loader =
                new FXMLLoader(resource);

            Parent root = loader.load();

            LookupDialogController controller =
                loader.getController();

            controller.setLookup(lookup);

            Stage stage = createDialogStage(
                root,
                "Edit Master"
            );

            stage.showAndWait();

            loadCategories();

            if (selectedType != null) {
                lstTypes.getSelectionModel()
                    .select(selectedType);
            }

            loadTable();

            if (controller.wasSaved()) {
                setStatus("Master record updated successfully.");
                showSuccess("Master Record Updated", "The selected master record was updated successfully.");
            } else {
                setStatus("Edit Master cancelled.");
            }

        } catch (IOException exception) {

            showError(
                "Could not open the Edit Master dialog:\n"
                    + exception.getMessage()
            );

            setStatus(
                "Could not open the Edit Master dialog."
            );
        }
    }

    private Stage createDialogStage(
        Parent root,
        String title
    ) {

        Stage stage = new Stage();

        PlatformUiSupport.configureDialogStage(stage, tblLookup, title, false);

        Scene scene = new Scene(root);
        ThemeManager.applyTheme(scene);

        stage.setScene(scene);
        stage.sizeToScene();


        return stage;
    }

    @FXML
    private void deleteLookup() {

        Lookup selectedLookup =
            tblLookup.getSelectionModel()
                .getSelectedItem();

        if (selectedLookup == null) {

            showWarning(
                "Select a master record to deactivate."
            );

            return;
        }

        Alert confirmation = new OwnedAlert(
            Alert.AlertType.CONFIRMATION,
            "Deactivate '"
                + selectedLookup.getLookupValue()
                + "'? Existing records will keep this value, but it will not be offered for future use.",
            ButtonType.YES,
            ButtonType.NO
        );

        confirmation.setTitle("Deactivate Master Record");
        confirmation.setHeaderText("Confirm deactivation");

        if (confirmation.showAndWait()
            .orElse(ButtonType.NO) != ButtonType.YES) {

            return;
        }

        try {

            service.delete(selectedLookup.getId());

            loadCategories();
            loadTable();

            setStatus(
                "Lookup deactivated successfully."
            );
            showSuccess("Master Record Deactivated",
                "Master record '" + selectedLookup.getLookupValue() + "' is now inactive for future use.");

        } catch (Exception exception) {

            showError(
                "Lookup could not be deactivated:\n"
                    + exception.getMessage()
            );

            setStatus(
                "Lookup could not be deactivated."
            );
        }
    }

    /* =========================================================
       REFRESH
       ========================================================= */

    @FXML
    private void refresh() {

        String selectedCategory =
            lstTypes.getSelectionModel().getSelectedItem();

        loadCategories();

        if (selectedCategory != null
            && lstTypes.getItems()
            .contains(selectedCategory)) {

            lstTypes.getSelectionModel()
                .select(selectedCategory);
        }

        loadTable();
        loadCategoryChart();

        setStatus("Master data refreshed.");
    }

    /* =========================================================
       UTILITIES
       ========================================================= */

    private void setLabelText(
        Label label,
        String text
    ) {

        if (label != null) {
            label.setText(text == null ? "" : text);
        }
    }

    private void setStatus(String message) {

        if (lblStatus != null) {
            lblStatus.setText(message);
        }
    }

    private void showSuccess(String header, String message) {
        Alert alert = new OwnedAlert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle("Success");
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private void showWarning(String message) {

        Alert alert = new OwnedAlert(
            Alert.AlertType.WARNING,
            message,
            ButtonType.OK
        );

        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String message) {

        Alert alert = new OwnedAlert(
            Alert.AlertType.ERROR,
            message,
            ButtonType.OK
        );

        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.showAndWait();
    }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colCode, "identity");
        IconFactory.applyTableHeaderIcon(colValue, "category");
        IconFactory.applyTableHeaderIcon(colDescription, "notes");
    }
    @FXML
    private void exportLookup() {
        FileChooser chooser = new FileChooser();
        String category = lstTypes.getSelectionModel().getSelectedItem();
        chooser.setInitialFileName((category == null ? "Master_Data" : category.replaceAll("[^A-Za-z0-9_-]", "_")) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        java.io.File selected = chooser.showSaveDialog(tblLookup.getScene().getWindow());
        if (selected == null) return;
        try (BufferedWriter writer = Files.newBufferedWriter(selected.toPath(), StandardCharsets.UTF_8)) {
            writer.write("Code,Value,Description");
            writer.newLine();
            for (Lookup row : tblLookup.getItems()) {
                writer.write(csv(row.getLookupCode()) + "," + csv(row.getLookupValue()) + "," + csv(row.getDescription()));
                writer.newLine();
            }
            setStatus("Master data exported successfully.");
        } catch (IOException ex) {
            new OwnedAlert(Alert.AlertType.ERROR, "Unable to export master data: " + ex.getMessage()).showAndWait();
        }
    }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

}
