package org.example.controller;


import org.example.util.IconFactory;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.database.DatabaseManager;
import org.example.model.Lookup;
import org.example.service.LookupService;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MasterDataController {

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

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    @FXML
    public void initialize() {
        configureKpiIcons();
        configureExplicitTableHeaderIcons();

        configureTableColumns();
        configureTableInteractions();
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


    private void configureKpiIcons() {
        if (kpiCategoriesIcon != null) kpiCategoriesIcon.getChildren().setAll(IconFactory.icon("category", 24));
        if (kpiValuesIcon != null) kpiValuesIcon.getChildren().setAll(IconFactory.icon("master", 24));
        if (kpiSelectedIcon != null) kpiSelectedIcon.getChildren().setAll(IconFactory.icon("select", 24));
        if (kpiStatusIcon != null) kpiStatusIcon.getChildren().setAll(IconFactory.icon("complete", 24));
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

        String previouslySelected =
            lstTypes.getSelectionModel().getSelectedItem();

        List<String> categories = new ArrayList<>();

        String sql = """
            SELECT category_name
            FROM master_category
            ORDER BY display_order, category_name
            """;

        try (
            Connection connection = DatabaseManager.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {

            while (resultSet.next()) {
                categories.add(resultSet.getString("category_name"));
            }

            allCategories.clear(); allCategories.addAll(categories);
            filterCategories(txtCategorySearch == null ? "" : txtCategorySearch.getText());

            updateCategoryCounts(categories.size());
            loadCategoryChart();

            if (previouslySelected != null
                && categories.contains(previouslySelected)) {

                lstTypes.getSelectionModel()
                    .select(previouslySelected);

            } else if (!categories.isEmpty()) {

                lstTypes.getSelectionModel().selectFirst();
            }

        } catch (SQLException exception) {

            showWarning(
                "Could not load Master Categories:\n"
                    + exception.getMessage()
            );

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

            ObservableList<Lookup> tableItems =
                FXCollections.observableArrayList(filteredList);

            tblLookup.setItems(tableItems);

            updateTableDashboard(
                selectedType,
                tableItems.size()
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

        if (pagination != null) {
            pagination.setPageCount(1);
            pagination.setCurrentPageIndex(0);
            pagination.setDisable(true);
        }

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

        tblLookup.setItems(
            FXCollections.observableArrayList()
        );

        setLabelText(lblValueCount, "0");
        setLabelText(lblDashboardValueCount, "0");
        setLabelText(lblSummaryValueCount, "0");

        setLabelText(lblSelectedCategory, "-");
        setLabelText(lblSummarySelectedCategory, "-");

        setLabelText(lblRecordCount, "0 Records");

        if (pagination != null) {
            pagination.setPageCount(1);
            pagination.setCurrentPageIndex(0);
            pagination.setDisable(true);
        }
    }

    /* =========================================================
       PIE CHART
       ========================================================= */

    private void loadCategoryChart() {

        if (categoryPieChart == null) {
            return;
        }

        ObservableList<PieChart.Data> chartData =
            FXCollections.observableArrayList();

        String sql = """
            SELECT
                mc.category_name,
                COUNT(lm.id) AS value_count
            FROM master_category mc
            LEFT JOIN lookup_master lm
                ON lm.lookup_type = mc.category_name
            GROUP BY mc.category_name
            ORDER BY value_count DESC, mc.category_name
            """;

        try (
            Connection connection = DatabaseManager.getConnection();
            PreparedStatement statement =
                connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {

            while (resultSet.next()) {

                String category =
                    resultSet.getString("category_name");

                int valueCount =
                    resultSet.getInt("value_count");

                if (valueCount > 0) {
                    chartData.add(
                        new PieChart.Data(
                            category,
                            valueCount
                        )
                    );
                }
            }

            categoryPieChart.setData(chartData);
            categoryPieChart.setAnimated(false);
            categoryPieChart.setLabelsVisible(false);
            categoryPieChart.setLegendVisible(true);

            if (chartData.isEmpty()) {
                categoryPieChart.setTitle(
                    "No Category Data"
                );
            } else {
                categoryPieChart.setTitle(
                    "Category Distribution"
                );
            }

        } catch (SQLException exception) {

            categoryPieChart.setData(
                FXCollections.observableArrayList()
            );

            categoryPieChart.setTitle(
                "Analytics Unavailable"
            );
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

        String selectedCategory =
            lstTypes.getSelectionModel().getSelectedItem();

        if (selectedCategory == null) {
            showWarning(
                "Select a Master Category to rename."
            );
            return;
        }

        editCategory(selectedCategory);
    }

    private void editCategory(String oldName) {

        boolean isNewCategory = oldName == null;

        TextInputDialog dialog =
            new TextInputDialog(
                isNewCategory ? "" : oldName
            );

        dialog.setTitle(
            isNewCategory
                ? "Add Master Category"
                : "Rename Master Category"
        );

        dialog.setHeaderText(
            isNewCategory
                ? "Create a category for related master values"
                : "Rename this category and all linked values"
        );

        dialog.setContentText("Category name:");

        dialog.showAndWait()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .ifPresent(value -> {

                String newName =
                    value.toUpperCase(Locale.ROOT);

                String categoryCode =
                    generateCategoryCode(newName);

                try (
                    Connection connection =
                        DatabaseManager.getConnection()
                ) {

                    connection.setAutoCommit(false);

                    try {

                        if (isNewCategory) {

                            insertCategory(
                                connection,
                                categoryCode,
                                newName
                            );

                        } else {

                            renameCategory(
                                connection,
                                oldName,
                                categoryCode,
                                newName
                            );
                        }

                        connection.commit();

                        loadCategories();

                        lstTypes.getSelectionModel()
                            .select(newName);

                        setStatus(
                            isNewCategory
                                ? "Category added successfully."
                                : "Category renamed successfully."
                        );

                    } catch (SQLException exception) {

                        connection.rollback();
                        throw exception;
                    }

                } catch (SQLException exception) {

                    showWarning(
                        "Master Category could not be saved. "
                            + "Use a unique name.\n"
                            + exception.getMessage()
                    );

                    setStatus("Category could not be saved.");
                }
            });
    }

    private void insertCategory(
        Connection connection,
        String categoryCode,
        String categoryName
    ) throws SQLException {

        String sql = """
            INSERT INTO master_category
                (category_code, category_name)
            VALUES (?, ?)
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {

            statement.setString(1, categoryCode);
            statement.setString(2, categoryName);
            statement.executeUpdate();
        }
    }

    private void renameCategory(
        Connection connection,
        String oldName,
        String categoryCode,
        String newName
    ) throws SQLException {

        String updateCategorySql = """
            UPDATE master_category
            SET category_name = ?,
                category_code = ?
            WHERE category_name = ?
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(
                    updateCategorySql
                )
        ) {

            statement.setString(1, newName);
            statement.setString(2, categoryCode);
            statement.setString(3, oldName);
            statement.executeUpdate();
        }

        String updateLookupSql = """
            UPDATE lookup_master
            SET lookup_type = ?
            WHERE lookup_type = ?
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(
                    updateLookupSql
                )
        ) {

            statement.setString(1, newName);
            statement.setString(2, oldName);
            statement.executeUpdate();
        }
    }

    private String generateCategoryCode(
        String categoryName
    ) {

        String code = categoryName
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");

        if (code.isBlank()) {
            code = "CATEGORY";
        }

        return code;
    }

    @FXML
    private void deleteCategory() {

        String selectedCategory =
            lstTypes.getSelectionModel().getSelectedItem();

        if (selectedCategory == null) {

            showWarning(
                "Select a Master Category to delete."
            );

            return;
        }

        Alert confirmation = new Alert(
            Alert.AlertType.CONFIRMATION,
            "Delete category '"
                + selectedCategory
                + "' and all of its values?",
            ButtonType.YES,
            ButtonType.NO
        );

        confirmation.setTitle("Delete Master Category");
        confirmation.setHeaderText(
            "Confirm category deletion"
        );

        if (confirmation.showAndWait()
            .orElse(ButtonType.NO) != ButtonType.YES) {

            return;
        }

        try (
            Connection connection =
                DatabaseManager.getConnection()
        ) {

            connection.setAutoCommit(false);

            try {

                deleteLookupsByCategory(
                    connection,
                    selectedCategory
                );

                deleteCategoryRecord(
                    connection,
                    selectedCategory
                );

                connection.commit();

                loadCategories();

                if (!lstTypes.getItems().isEmpty()) {
                    lstTypes.getSelectionModel().selectFirst();
                } else {
                    clearTable();
                }

                setStatus(
                    "Category deleted successfully."
                );

            } catch (SQLException exception) {

                connection.rollback();
                throw exception;
            }

        } catch (SQLException exception) {

            showWarning(
                "Category could not be deleted:\n"
                    + exception.getMessage()
            );

            setStatus("Category could not be deleted.");
        }
    }

    private void deleteLookupsByCategory(
        Connection connection,
        String categoryName
    ) throws SQLException {

        String sql = """
            DELETE FROM lookup_master
            WHERE lookup_type = ?
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {

            statement.setString(1, categoryName);
            statement.executeUpdate();
        }
    }

    private void deleteCategoryRecord(
        Connection connection,
        String categoryName
    ) throws SQLException {

        String sql = """
            DELETE FROM master_category
            WHERE category_name = ?
            """;

        try (
            PreparedStatement statement =
                connection.prepareStatement(sql)
        ) {

            statement.setString(1, categoryName);
            statement.executeUpdate();
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

            setStatus(
                "Lookup dialog closed. Data refreshed."
            );

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

            setStatus(
                "Lookup dialog closed. Data refreshed."
            );

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
                "Select a master record to delete."
            );

            return;
        }

        Alert confirmation = new Alert(
            Alert.AlertType.CONFIRMATION,
            "Delete '"
                + selectedLookup.getLookupValue()
                + "'? This cannot be undone.",
            ButtonType.YES,
            ButtonType.NO
        );

        confirmation.setTitle("Delete Master Record");
        confirmation.setHeaderText("Confirm deletion");

        if (confirmation.showAndWait()
            .orElse(ButtonType.NO) != ButtonType.YES) {

            return;
        }

        try {

            service.delete(selectedLookup.getId());

            loadCategories();
            loadTable();

            setStatus(
                "Lookup deleted successfully."
            );

        } catch (Exception exception) {

            showError(
                "Lookup could not be deleted:\n"
                    + exception.getMessage()
            );

            setStatus(
                "Lookup could not be deleted."
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

    private void showWarning(String message) {

        Alert alert = new Alert(
            Alert.AlertType.WARNING,
            message,
            ButtonType.OK
        );

        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String message) {

        Alert alert = new Alert(
            Alert.AlertType.ERROR,
            message,
            ButtonType.OK
        );

        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.showAndWait();
    }


    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colCode, "document");
        IconFactory.applyTableHeaderIcon(colValue, "item");
        IconFactory.applyTableHeaderIcon(colDescription, "document");
    }
}
