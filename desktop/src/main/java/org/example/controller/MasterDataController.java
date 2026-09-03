package org.example.controller;

import org.example.util.OwnedAlert;
import org.example.util.OwnedTextInputDialog;


import org.example.util.IconFactory;
import org.example.util.RegisterDetailDrawer;
import org.example.util.RegisterUiSupport;
import org.example.util.OperationalUiSupport;
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
import javafx.scene.layout.VBox;
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
import org.example.util.UiTaskExecutor;
import org.example.util.FxDebouncer;
import org.example.shortcut.ShortcutRegistry;
import org.example.shortcut.ShortcutRegistry.Action;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;

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
    private static volatile String requestedCategory;
    public static void requestCategory(String category) { requestedCategory = category; }

    @FXML private BorderPane root;

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
    private TableColumn<Lookup, String> colLookupStatus;

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
    private final FxDebouncer searchDebouncer = new FxDebouncer(java.time.Duration.ofMillis(220));
    private List<Lookup> filteredLookups = List.of();
    private boolean updatingPagination;
    private RegisterDetailDrawer detailDrawer;
    private Lookup detailLookup;
    private final java.util.Map<String, MasterCategoryService.Category> categoryByName = new java.util.LinkedHashMap<>();

    /* =========================================================
       INITIALIZATION
       ========================================================= */

    @FXML
    public void initialize() {
        configureKpiIcons();
        configureSectionIcons();
        configureActionIcons();
        configureCategoryListCells();
        configureTableColumns();
        configureTableInteractions();
        installDetailDrawer();
        configurePagination();
        configureListeners();
        configureKeyboardShortcuts();

        setStatus("Loading master data...");

        loadCategories();
    }



    @Override
    public void onScreenShown(boolean reusedFromCache) {
        if (!reusedFromCache) return;
        loadCategories();
    }

    @Override
    public void onScreenHidden() {
        UiTaskExecutor.cancel("master-categories");
        UiTaskExecutor.cancel("master-values");
        searchDebouncer.cancel();
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
                MasterCategoryService.Category meta = categoryByName.get(category);
                boolean active = meta == null || meta.active();
                long totalValues = meta == null ? 0 : meta.valueCount();
                long activeValues = meta == null ? 0 : meta.activeValueCount();

                Label name = new Label(category);
                name.getStyleClass().add("master-category-name");
                Label activity = new Label(activeValues + " active out of " + totalValues);
                activity.getStyleClass().add("master-category-activity");
                VBox text = new VBox(2, name, activity);
                HBox.setHgrow(text, Priority.ALWAYS);

                Label state = new Label(active ? "ACTIVE" : "INACTIVE");
                state.getStyleClass().addAll("master-category-state", active ? "master-state-active" : "master-state-inactive");
                state.setGraphic(IconFactory.statusIcon(active ? "complete" : "cancel", active ? "success" : "danger"));
                state.setGraphicTextGap(5);

                HBox content = new HBox(10, IconFactory.icon(semantic, 18), text, state);
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

        // ROLE is special: lookup_value is the security/business identity. The generic ROLxxx
        // lookup_code remains an internal Master identifier and must never be presented as a role ID.
        colCode.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(displayLookupCode(value.getValue())));

        colValue.setCellValueFactory(
            new PropertyValueFactory<>("lookupValue")
        );

        colDescription.setCellValueFactory(
            new PropertyValueFactory<>("description")
        );

        if (colLookupStatus != null) {
            colLookupStatus.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(value.getValue().isActive() ? "Active" : "Inactive"));
            colLookupStatus.setCellFactory(column -> new TableCell<>() {
                @Override protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : value);
                    setGraphic(null);
                    getStyleClass().removeAll("master-lookup-active", "master-lookup-inactive");
                    if (empty || value == null) return;
                    boolean active = "Active".equalsIgnoreCase(value);
                    setGraphic(IconFactory.statusIcon(active ? "complete" : "cancel", active ? "success" : "danger"));
                    setGraphicTextGap(6);
                    getStyleClass().add(active ? "master-lookup-active" : "master-lookup-inactive");
                }
            });
        }
    }

    private boolean isRoleCategory(String category) {
        return category != null && "ROLE".equalsIgnoreCase(category.trim());
    }

    private boolean isRoleCategorySelected() {
        return lstTypes != null && isRoleCategory(lstTypes.getSelectionModel().getSelectedItem());
    }

    private String displayLookupCode(Lookup lookup) {
        if (lookup == null) return "";
        if (!isRoleCategorySelected() && !isRoleCategory(lookup.getLookupType())) return safeText(lookup.getLookupCode());
        return safeText(lookup.getLookupValue()).trim().toUpperCase(Locale.ROOT);
    }

    private void updateLookupColumnLabels(String category) {
        boolean role = isRoleCategory(category);
        if (colCode != null) colCode.setText(role ? "Role Code" : "Code");
        if (colValue != null) colValue.setText(role ? "Role Name" : "Value");
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private void configureListeners() {

        lstTypes.getSelectionModel()
            .selectedItemProperty()
            .addListener((observable, oldValue, newValue) -> {

                if (newValue != null) {
                    String semantic = categorySemantic(newValue);
                    if (kpiSelectedIcon != null) kpiSelectedIcon.getChildren().setAll(IconFactory.icon(semantic, 24));
                    if (lookupValuesIcon != null) lookupValuesIcon.getChildren().setAll(IconFactory.icon(semantic, 18));
                    if (root != null) {
                        root.getStyleClass().remove("master-role-selected");
                        if ("ROLE".equalsIgnoreCase(newValue)) root.getStyleClass().add("master-role-selected");
                    }
                    updateCategoryActionState(newValue);
                    updateLookupColumnLabels(newValue);
                    loadTable();
                } else {
                    clearTable();
                }
            });

        txtSearch.textProperty()
            .addListener((observable, oldValue, newValue) -> searchDebouncer.submit(this::loadTable));

        if (txtCategorySearch != null) {
            txtCategorySearch.textProperty().addListener((observable, oldValue, newValue) -> filterCategories(newValue));
        }
        tblLookup.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateLookupActionState(newValue));
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
                if (row.isEmpty() || event.getButton() != MouseButton.PRIMARY ||
                    RegisterUiSupport.isInteractiveTableTarget(event.getPickResult().getIntersectedNode(), row)) return;
                if (event.getClickCount() == 1) {
                    Lookup clicked = row.getItem();
                    if (detailDrawer != null && detailDrawer.isOpen() && detailLookup == clicked) closeLookupDetails();
                    else {
                        tblLookup.getSelectionModel().select(clicked);
                        showLookupDetails(clicked);
                    }
                    event.consume();
                }
            });
            return row;
        });
    }

    private void installDetailDrawer() {
        detailDrawer = new RegisterDetailDrawer();
        detailDrawer.attachBesideTable(tblLookup);
        OperationalUiSupport.installEscapeClose(tblLookup, detailDrawer::isOpen, this::closeLookupDetails);
    }

    @FXML
    private void viewSelectedLookup() {
        Lookup selected = tblLookup.getSelectionModel().getSelectedItem();
        if (selected == null) { showWarning("Select a master record to view."); return; }
        showLookupDetails(selected);
    }

    private void showLookupDetails(Lookup lookup) {
        if (lookup == null || detailDrawer == null) return;
        detailLookup = lookup;
        String category = lookup.getLookupType() == null || lookup.getLookupType().isBlank()
            ? String.valueOf(lstTypes.getSelectionModel().getSelectedItem()) : lookup.getLookupType();
        String status = lookup.isActive() ? "Active" : "Inactive";
        boolean role = isRoleCategory(category);
        String displayCode = displayLookupCode(lookup);
        List<RegisterDetailDrawer.Field> fields = new ArrayList<>();
        fields.add(RegisterDetailDrawer.field("Master Category", category, categorySemantic(category)));
        fields.add(RegisterDetailDrawer.field(role ? "Role Code" : "Code", displayCode, "reference"));
        fields.add(RegisterDetailDrawer.field(role ? "Role Name" : "Value", lookup.getLookupValue(), "details"));
        fields.add(RegisterDetailDrawer.field("Description", lookup.getDescription(), "notes"));
        fields.add(RegisterDetailDrawer.field("Display Order", String.valueOf(lookup.getDisplayOrder()), "sort"));
        fields.add(RegisterDetailDrawer.field("Status", status, lookup.isActive() ? "active" : "inactive"));
        detailDrawer.showRecord(lookup.getLookupValue(), displayCode, fields);
        Button editButton = new Button("Edit Master", IconFactory.compactIcon("edit", 15));
        editButton.getStyleClass().addAll("approved-button", "approved-secondary-button");
        editButton.setOnAction(event -> { tblLookup.getSelectionModel().select(lookup); editLookup(); });
        detailDrawer.setActions(editButton);
    }

    private void closeLookupDetails() {
        detailLookup = null;
        if (detailDrawer != null) detailDrawer.hideDrawer();
        if (tblLookup != null) tblLookup.getSelectionModel().clearSelection();
    }

    private void configureKeyboardShortcuts() {
        if (root == null) return;
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event == null || event.isConsumed()) return;
            boolean textInput = event.getTarget() instanceof TextInputControl || event.getTarget() instanceof ComboBoxBase<?>;
            if (!textInput && ShortcutRegistry.matches(event, Action.MASTER_DELETE) && ShortcutRegistry.permitted(Action.MASTER_DELETE)) { deleteLookup(); event.consume(); return; }
            if (!textInput && ShortcutRegistry.matches(event, Action.MASTER_EDIT) && ShortcutRegistry.permitted(Action.MASTER_EDIT)) {
                if (tblLookup.getSelectionModel().getSelectedItem() != null) editLookup();
                event.consume(); return;
            }
            if (ShortcutRegistry.matches(event, Action.MASTER_REFRESH) && ShortcutRegistry.permitted(Action.MASTER_REFRESH)) { refresh(); event.consume(); return; }
            if (ShortcutRegistry.matches(event, Action.MASTER_NEW) && ShortcutRegistry.permitted(Action.MASTER_NEW)) { addLookup(); event.consume(); }
        });
    }

    /* =========================================================
       CATEGORY LOADING
       ========================================================= */

    private void loadCategories() {
        String previouslySelected = lstTypes.getSelectionModel().getSelectedItem();
        String requested = requestedCategory;
        setStatus("Loading master data...");
        UiTaskExecutor.submitLatest(
            "master-categories",
            categoryService::getAll,
            rows -> applyCategories(rows == null ? List.of() : rows, previouslySelected, requested),
            failure -> { showWarning("Could not load Master Categories:\n" + rootMessage(failure)); setStatus("Failed to load categories."); }
        );
    }

    private void applyCategories(List<MasterCategoryService.Category> rows, String previouslySelected, String requested) {
        categoryByName.clear();
        rows.forEach(row -> categoryByName.put(row.name(), row));
        List<String> categories = rows.stream().map(MasterCategoryService.Category::name).toList();
        allCategories.clear(); allCategories.addAll(categories);
        lstTypes.refresh();
        filterCategories(txtCategorySearch == null ? "" : txtCategorySearch.getText());
        updateCategoryCounts(categories.size());
        applyCategoryChart(rows);

        String requestedTarget = requested;
        boolean requestedAvailable = requestedTarget != null && categories.stream().anyMatch(v -> v.equalsIgnoreCase(requestedTarget));
        String target = requestedAvailable ? requestedTarget : previouslySelected;
        if (target != null) {
            String finalTarget = target;
            categories.stream().filter(v -> v.equalsIgnoreCase(finalTarget)).findFirst().ifPresent(v -> lstTypes.getSelectionModel().select(v));
        }
        if (lstTypes.getSelectionModel().getSelectedItem() == null && !categories.isEmpty()) lstTypes.getSelectionModel().selectFirst();
        requestedCategory = null;
        if (categories.isEmpty()) { clearTable(); setStatus("No master categories found."); }
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
        String selectedType = lstTypes.getSelectionModel().getSelectedItem();
        if (selectedType == null || selectedType.isBlank()) { clearTable(); return; }
        String searchText = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        UiTaskExecutor.submitLatest(
            "master-values",
            () -> service.getByType(selectedType),
            lookupList -> {
                if (!selectedType.equals(lstTypes.getSelectionModel().getSelectedItem())) return;
                List<Lookup> safe = lookupList == null ? List.of() : lookupList;
                filteredLookups = List.copyOf(safe.stream().filter(lookup -> matchesSearch(lookup, searchText)).toList());
                updateTableDashboard(selectedType, filteredLookups.size());
            },
            failure -> { clearTable(); showWarning("Could not load lookup values:\n" + rootMessage(failure)); setStatus("Failed to load lookup values."); }
        );
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

        MasterCategoryService.Category category = categoryByName.get(selectedType);
        String selectedLabel = selectedType;
        if (category != null) selectedLabel += category.active() ? " • ACTIVE" : " • INACTIVE";
        setLabelText(lblSelectedCategory, selectedLabel);
        setLabelText(lblSummarySelectedCategory, selectedLabel);

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


    private void applyCategoryChart(List<MasterCategoryService.Category> rows) {
        if (categoryPieChart == null) return;
        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        for (MasterCategoryService.Category row : rows) if (row.valueCount() > 0) chartData.add(new PieChart.Data(row.name(), row.valueCount()));
        categoryPieChart.setData(chartData);
        categoryPieChart.setAnimated(false);
        categoryPieChart.setLabelsVisible(false);
        categoryPieChart.setLegendVisible(true);
        categoryPieChart.setTitle(chartData.isEmpty() ? "No Category Data" : "Category Distribution");
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
            UiTaskExecutor.submitAction(
                    "master-category-save-" + newName,
                    () -> { if (isNewCategory) categoryService.add(newName); else categoryService.rename(oldName, newName, categoryByName.get(oldName) == null ? 0L : categoryByName.get(oldName).rowVersion()); return null; },
                    ignored -> {
                        loadCategories();
                        lstTypes.getSelectionModel().select(newName);
                        String successMessage = isNewCategory ? "Category added successfully." : "Category renamed successfully.";
                        setStatus(successMessage);
                        showSuccess(isNewCategory ? "Category Added" : "Category Renamed", successMessage);
                    },
                    failure -> {
                        showWarning("Master Category could not be saved. Use a unique name.\n" + rootMessage(failure));
                        setStatus("Category could not be saved.");
                    }
            );
        });
    }







    @FXML
    private void deleteCategory() {
        String selectedCategory = lstTypes.getSelectionModel().getSelectedItem();
        if (selectedCategory == null) { showWarning("Select a Master Category first."); return; }
        MasterCategoryService.Category meta = categoryByName.get(selectedCategory);
        boolean currentlyActive = meta == null || meta.active();
        if (!currentlyActive) {
            Alert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION,
                "Reactivate category '" + selectedCategory + "'? Existing lookup values stay in their current Active/Inactive state so you can enable only the values you want.", ButtonType.YES, ButtonType.NO);
            confirmation.setTitle("Reactivate Master Category"); confirmation.setHeaderText("Confirm category reactivation");
            if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
            UiTaskExecutor.submitAction(
                    "master-category-reactivate-" + selectedCategory,
                    () -> { categoryService.setActive(selectedCategory, true, meta == null ? 0L : meta.rowVersion()); return null; },
                    ignored -> {
                        loadCategories(); lstTypes.getSelectionModel().select(selectedCategory); loadTable();
                        setStatus("Category reactivated successfully.");
                        showSuccess("Category Reactivated", "Master category '" + selectedCategory + "' is active again. Reactivate individual lookup values as needed.");
                    },
                    failure -> { showWarning("Category could not be reactivated:\n" + rootMessage(failure)); setStatus("Category could not be reactivated."); }
            );
            return;
        }
        Alert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION,
            "Deactivate category '" + selectedCategory + "' and all of its values? Existing records will remain unchanged.", ButtonType.YES, ButtonType.NO);
        confirmation.setTitle("Deactivate Master Category"); confirmation.setHeaderText("Confirm category deactivation");
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        UiTaskExecutor.submitAction(
                "master-category-deactivate-" + selectedCategory,
                () -> { categoryService.delete(selectedCategory, meta == null ? 0L : meta.rowVersion()); return null; },
                ignored -> {
                    loadCategories(); lstTypes.getSelectionModel().select(selectedCategory); loadTable();
                    setStatus("Category deactivated successfully.");
                    showSuccess("Category Deactivated", "Master category '" + selectedCategory + "' and its values are now inactive for future use.");
                },
                failure -> { showWarning("Category could not be deactivated:\n" + rootMessage(failure)); setStatus("Category could not be deactivated."); }
        );
    }

    private void updateCategoryActionState(String categoryName) {
        if (btnDeleteCategory == null) return;
        MasterCategoryService.Category meta = categoryByName.get(categoryName);
        boolean active = meta == null || meta.active();
        btnDeleteCategory.setText(active ? "Deactivate Category" : "Reactivate Category");
        UiActionIcons.apply(btnDeleteCategory, active ? "delete" : "restore");
        boolean protectedRoleCategory = categoryName != null && "ROLE".equalsIgnoreCase(categoryName.trim());
        btnRenameCategory.setDisable(categoryName == null || protectedRoleCategory);
        btnDeleteCategory.setDisable(categoryName == null || protectedRoleCategory);
        if (protectedRoleCategory) {
            btnDeleteCategory.setTooltip(new Tooltip("Role Master is a protected system category. Manage its role values in the table."));
            btnRenameCategory.setTooltip(new Tooltip("Role Master keeps the fixed category code ROLE so every security screen uses one source."));
        } else {
            btnDeleteCategory.setTooltip(null);
            btnRenameCategory.setTooltip(null);
        }
    }

    private void updateLookupActionState(Lookup lookup) {
        if (btnDeleteLookup == null) return;
        boolean active = lookup == null || lookup.isActive();
        btnDeleteLookup.setText(active ? "Deactivate" : "Reactivate");
        UiActionIcons.apply(btnDeleteLookup, active ? "delete" : "restore");
        btnEditLookup.setDisable(lookup == null);
        btnDeleteLookup.setDisable(lookup == null);
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

            URL resource = org.example.util.ResourceLocator.require(
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

            URL resource = org.example.util.ResourceLocator.require(
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

        org.example.util.ProfessionalUiEnhancer.enhance(root);
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
        Lookup selectedLookup = tblLookup.getSelectionModel().getSelectedItem();
        if (selectedLookup == null) { showWarning("Select a master record first."); return; }
        boolean activate = !selectedLookup.isActive();
        String verb = activate ? "Reactivate" : "Deactivate";
        String detail = activate
            ? "Reactivate '" + selectedLookup.getLookupValue() + "' for future use?"
            : "Deactivate '" + selectedLookup.getLookupValue() + "'? Existing records will keep this value, but it will not be offered for future use.";
        Alert confirmation = new OwnedAlert(Alert.AlertType.CONFIRMATION, detail, ButtonType.YES, ButtonType.NO);
        confirmation.setTitle(verb + " Master Record"); confirmation.setHeaderText("Confirm " + verb.toLowerCase(Locale.ROOT));
        if (confirmation.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
        String selectedType = lstTypes.getSelectionModel().getSelectedItem();
        UiTaskExecutor.submitAction(
                "master-lookup-status-" + selectedLookup.getId(),
                () -> { if (activate) service.setActive(selectedLookup, true); else service.delete(selectedLookup); return null; },
                ignored -> {
                    loadCategories();
                    if (selectedType != null) lstTypes.getSelectionModel().select(selectedType);
                    loadTable();
                    setStatus("Lookup " + (activate ? "reactivated" : "deactivated") + " successfully.");
                    showSuccess("Master Record " + (activate ? "Reactivated" : "Deactivated"),
                            "Master record '" + selectedLookup.getLookupValue() + "' is now " + (activate ? "active" : "inactive") + " for future use.");
                },
                failure -> {
                    showError("Lookup could not be " + (activate ? "reactivated" : "deactivated") + ":\n" + rootMessage(failure));
                    setStatus("Lookup status could not be changed.");
                }
        );
    }


    /* =========================================================
       REFRESH
       ========================================================= */

    @FXML
    private void refresh() {
        loadCategories();
    }

    /* =========================================================
       UTILITIES
       ========================================================= */

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? "Operation failed" : message;
    }

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
        org.example.util.ToastManager.success(tblLookup, header, message);
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
    @FXML
    private void exportLookup() {
        org.example.service.PermissionService.require("MASTERS.EXPORT", "Export Master Data");
        FileChooser chooser = new FileChooser();
        String category = lstTypes.getSelectionModel().getSelectedItem();
        chooser.setInitialFileName((category == null ? "Master_Data" : category.replaceAll("[^A-Za-z0-9_-]", "_")) + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        java.io.File selected = chooser.showSaveDialog(tblLookup.getScene().getWindow());
        if (selected == null) return;
        try (BufferedWriter writer = Files.newBufferedWriter(selected.toPath(), StandardCharsets.UTF_8)) {
            boolean role = isRoleCategory(category);
            writer.write(role ? "Role Code,Role Name,Description" : "Code,Value,Description");
            writer.newLine();
            for (Lookup row : tblLookup.getItems()) {
                writer.write(csv(displayLookupCode(row)) + "," + csv(row.getLookupValue()) + "," + csv(row.getDescription()));
                writer.newLine();
            }
            setStatus("Master data exported successfully.");
        } catch (IOException ex) {
            new OwnedAlert(Alert.AlertType.ERROR, "Unable to export master data: " + ex.getMessage()).showAndWait();
        }
    }

    private String csv(String value) {
        String text = value == null ? "" : value;
        String trimmed = text.stripLeading();
        if (!trimmed.isEmpty()) {
            char first = trimmed.charAt(0);
            boolean numericNegative = first == '-' && trimmed.matches("-\\d+(?:\\.\\d+)?");
            if (first == '=' || first == '+' || first == '@' || (first == '-' && !numericNegative)) text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

}
