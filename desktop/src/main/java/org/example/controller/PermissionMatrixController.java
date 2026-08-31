package org.example.controller;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.example.api.admin.AdminApiClient;
import org.example.service.PermissionService;
import org.example.shared.PermissionCatalog;
import org.example.util.IconFactory;
import org.example.util.ModernDialog;
import org.example.util.ToastManager;
import org.example.util.UiTaskExecutor;
import org.example.navigation.ScreenLifecycle;

import java.util.*;
import java.util.function.Consumer;

/**
 * Dense, metadata-driven permission editor. Server permission records remain the
 * authority; this controller only reshapes them into a compact module x capability
 * matrix so new catalog rows do not require new FXML checkboxes.
 */
public class PermissionMatrixController implements ScreenLifecycle {
    private static String requestedRole;
    public static void requestRole(String role) { requestedRole = role; }

    @FXML private VBox root;
    @FXML private ComboBox<String> cmbRole, cmbCopyRole;
    @FXML private ComboBox<PermissionCatalog.Template> cmbTemplate;
    @FXML private TextField txtSearch;
    @FXML private CheckBox chkGrantedOnly;
    @FXML private Label lblGrantedSummary, lblRoleUsers, lblHint, lblLegend, lblPreviewTitle;
    @FXML private VBox previewContent;
    @FXML private TableView<MatrixRow> table;
    @FXML private TableColumn<MatrixRow, MatrixRow> colModule, colAll, colView, colCreate, colEdit, colDelete, colExport, colApprove, colSpecial;
    @FXML private Button btnBack, btnApplyTemplate, btnCopyRole, btnReset, btnSave;

    private final AdminApiClient api = new AdminApiClient();
    private final ObservableList<MatrixRow> tableRows = FXCollections.observableArrayList();
    private final List<ModuleRow> modules = new ArrayList<>();
    private final Map<String, Long> roleUserCounts = new HashMap<>();
    private final Map<String, String> roleDisplayNames = new HashMap<>();
    private final Map<String, CheckBox> headerToggles = new HashMap<>();
    private boolean loading;

    @FXML private void initialize() {
        configureIcons();
        configureTable();
        configureFilters();
        StringConverter<String> roleConverter = new StringConverter<>() {
            @Override public String toString(String code) { return code == null ? "" : roleDisplayNames.getOrDefault(code.toUpperCase(Locale.ROOT), code); }
            @Override public String fromString(String value) { return value; }
        };
        cmbRole.setConverter(roleConverter);
        cmbCopyRole.setConverter(roleConverter);
        cmbTemplate.getItems().setAll(PermissionCatalog.TEMPLATES);
        cmbRole.valueProperty().addListener((obs, oldRole, newRole) -> {
            if (!loading) loadRole(newRole);
        });
        loadRoles();
    }

    @Override
    public void onScreenShown(boolean reusedFromCache) {
        if (reusedFromCache) loadRoles();
    }

    private void configureIcons() {
        if (btnBack != null) btnBack.setGraphic(IconFactory.compactIcon("previous", 16));
        if (btnApplyTemplate != null) btnApplyTemplate.setGraphic(IconFactory.compactIcon("permission", 16));
        if (btnCopyRole != null) btnCopyRole.setGraphic(IconFactory.compactIcon("copy", 16));
        if (btnReset != null) btnReset.setGraphic(IconFactory.compactIcon("reset", 16));
        if (btnSave != null) btnSave.setGraphic(IconFactory.compactIcon("save", 16));
    }

    private void configureFilters() {
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> rebuildVisibleRows());
        chkGrantedOnly.selectedProperty().addListener((obs, oldValue, newValue) -> rebuildVisibleRows());
    }

    private void configureTable() {
        table.setItems(tableRows);
        table.setEditable(true);
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(MatrixRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("permission-group-row", "permission-module-row");
                if (!empty && item != null) getStyleClass().add(item.group() ? "permission-group-row" : "permission-module-row");
            }
        });

        colModule.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        colModule.setCellFactory(c -> new ModuleCell());
        configureAllColumn();
        configureActionColumn(colView, "VIEW", "View");
        configureActionColumn(colCreate, "CREATE", "Create");
        configureActionColumn(colEdit, "EDIT", "Edit");
        configureActionColumn(colDelete, "DELETE", "Delete");
        configureActionColumn(colExport, "EXPORT", "Export");
        configureActionColumn(colApprove, "APPROVE", "Approve");
        configureSpecialColumn();
    }

    private void configureAllColumn() {
        colAll.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        colAll.setCellFactory(c -> new MatrixCheckCell(row -> row.permissions(), "All"));
        installHeaderToggle(colAll, null, "All");
    }

    private void configureActionColumn(TableColumn<MatrixRow, MatrixRow> column, String action, String label) {
        column.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        column.setCellFactory(c -> new MatrixCheckCell(row -> {
            PermissionEntry entry = row.permission(action);
            return entry == null ? List.of() : List.of(entry);
        }, label));
        installHeaderToggle(column, action, label);
    }

    private void installHeaderToggle(TableColumn<MatrixRow, MatrixRow> column, String action, String label) {
        Label text = new Label(label);
        text.getStyleClass().add("permission-column-title");
        CheckBox toggle = new CheckBox();
        toggle.setAllowIndeterminate(false);
        toggle.getStyleClass().add("permission-header-check");
        toggle.setTooltip(new Tooltip(action == null ? "Grant or revoke every visible capability" : "Grant or revoke " + label + " for visible modules"));
        toggle.setOnAction(event -> {
            if (!canEdit()) return;
            boolean selected = toggle.isSelected();
            toggle.setIndeterminate(false);
            for (ModuleRow row : visibleModules()) {
                Collection<PermissionEntry> targets = action == null ? row.permissions() : entryList(row.permission(action));
                targets.forEach(p -> p.allowed().set(selected));
            }
            permissionsChanged();
        });
        HBox header = new HBox(5, text, toggle);
        header.setAlignment(Pos.CENTER);
        column.setText(null);
        column.setGraphic(header);
        headerToggles.put(action == null ? "*" : action, toggle);
    }

    private void configureSpecialColumn() {
        colSpecial.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        colSpecial.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(MatrixRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null || row.group()) { setGraphic(null); return; }
                List<PermissionEntry> special = ((ModuleRow) row).permissions().stream()
                        .filter(p -> !PermissionCatalog.isCommonAction(p.action()))
                        .sorted(Comparator.comparing(PermissionEntry::action))
                        .toList();
                if (special.isEmpty()) {
                    Label dash = new Label("—");
                    dash.getStyleClass().add("permission-not-supported");
                    setGraphic(dash);
                    setAlignment(Pos.CENTER);
                    return;
                }
                if (special.size() == 1) {
                    PermissionEntry entry = special.getFirst();
                    CheckBox toggle = new CheckBox();
                    toggle.getStyleClass().add("permission-special-check");
                    toggle.setSelected(entry.allowed().get());
                    toggle.setDisable(!canEdit());
                    toggle.setTooltip(new Tooltip(PermissionCatalog.actionLabel(entry.action())
                        + (entry.description() == null || entry.description().isBlank() ? "" : "\n" + entry.description())));
                    toggle.setOnAction(event -> {
                        entry.allowed().set(toggle.isSelected());
                        permissionsChanged();
                    });
                    setGraphic(toggle);
                    setAlignment(Pos.CENTER);
                    return;
                }
                MenuButton menu = new MenuButton();
                menu.getStyleClass().add("permission-special-menu");
                // This cell is a compact permission selector, not a row action.
                // Prevent the global action-icon decorator from turning the
                // useful "n of n" value into an unexplained icon-only button.
                menu.getProperties().put("erp.icon.skip", true);
                menu.getProperties().put("erp-icon-preserve", true);
                menu.setGraphic(null);
                menu.setContentDisplay(ContentDisplay.TEXT_ONLY);
                menu.setDisable(!canEdit());
                int granted = 0;
                for (PermissionEntry entry : special) {
                    if (entry.allowed().get()) granted++;
                    CheckMenuItem item = new CheckMenuItem(PermissionCatalog.actionLabel(entry.action()));
                    item.setSelected(entry.allowed().get());
                    item.setDisable(!canEdit());
                    if (entry.description() != null && !entry.description().isBlank()) item.setGraphic(IconFactory.compactIcon("permission", 13));
                    item.setOnAction(event -> {
                        entry.allowed().set(item.isSelected());
                        permissionsChanged();
                    });
                    menu.getItems().add(item);
                }
                menu.setText(granted + " / " + special.size());
                String labels = special.stream().map(p -> PermissionCatalog.actionLabel(p.action())).reduce((a,b) -> a + ", " + b).orElse("Special permissions");
                menu.setTooltip(new Tooltip("Special permissions: " + labels + "\nClick to review or change these permissions."));
                menu.setAccessibleText(granted + " of " + special.size() + " special permissions granted");
                setGraphic(menu);
                setAlignment(Pos.CENTER);
            }
        });
        Label label = new Label("Special");
        label.getStyleClass().add("permission-column-title");
        colSpecial.setText(null);
        colSpecial.setGraphic(label);
    }

    private void loadRoles() {
        UiTaskExecutor.submitLatest(
                "permission-matrix-roles",
                () -> api.roles().stream().filter(AdminApiClient.RoleDto::active).toList(),
                this::applyRoles,
                failure -> { loading = false; showError("Roles could not be loaded", asException(failure)); }
        );
    }

    private void applyRoles(List<AdminApiClient.RoleDto> roles) {
        loading = true;
        try {
            roleDisplayNames.clear();
            roleUserCounts.clear();
            for (var role : roles == null ? List.<AdminApiClient.RoleDto>of() : roles) {
                String code = role.code() == null ? "" : role.code().trim().toUpperCase(Locale.ROOT);
                if (code.isBlank()) continue;
                roleDisplayNames.put(code, role.displayName() == null || role.displayName().isBlank() ? code : role.displayName().trim());
                roleUserCounts.put(code, role.userCount());
            }
            cmbRole.getItems().setAll(roleDisplayNames.keySet().stream().toList());
            String requested = requestedRole == null ? null : requestedRole.trim().toUpperCase(Locale.ROOT);
            String target = requested != null && cmbRole.getItems().contains(requested)
                    ? requested : (cmbRole.getItems().isEmpty() ? null : cmbRole.getItems().getFirst());
            if (target != null) cmbRole.setValue(target);
            requestedRole = null;
        } finally {
            loading = false;
        }
        loadRole(cmbRole.getValue());
    }

    private void loadRole(String role) {
        modules.clear();
        tableRows.clear();
        if (role == null || role.isBlank()) return;
        UiTaskExecutor.submitLatest(
                "permission-matrix-role",
                () -> api.permissions(role),
                permissions -> applyRolePermissions(role, permissions),
                failure -> showError("Permissions could not be loaded", asException(failure))
        );
    }

    private void applyRolePermissions(String role, List<AdminApiClient.PermissionDto> permissions) {
        if (!Objects.equals(cmbRole.getValue(), role)) return;
        Map<String, ModuleRow> byModule = new LinkedHashMap<>();
        for (var dto : permissions == null ? List.<AdminApiClient.PermissionDto>of() : permissions) {
            String moduleKey = PermissionCatalog.normalize(dto.module());
            PermissionCatalog.ModuleMeta meta = PermissionCatalog.module(moduleKey);
            ModuleRow row = byModule.computeIfAbsent(moduleKey, key -> new ModuleRow(meta));
            PermissionEntry entry = new PermissionEntry(dto.id(), moduleKey, PermissionCatalog.normalize(dto.action()), dto.description(), new SimpleBooleanProperty(dto.allowed()));
            entry.allowed().addListener((obs, oldValue, newValue) -> permissionsChanged());
            row.add(entry);
        }
        modules.clear();
        modules.addAll(byModule.values().stream()
                .sorted(Comparator.comparingInt((ModuleRow row) -> row.meta().order()).thenComparing(row -> row.meta().label()))
                .toList());
        boolean admin = "ADMIN".equalsIgnoreCase(role);
        setEditingEnabled(!admin);
        long users = roleUserCounts.getOrDefault(role.toUpperCase(Locale.ROOT), 0L);
        lblRoleUsers.setText(users + (users == 1 ? " user" : " users") + " use this role");
        String displayRole = roleDisplayNames.getOrDefault(role.toUpperCase(Locale.ROOT), role);
        lblHint.setText(admin
                ? "Administrator is protected full access. Review is available, but the matrix cannot be changed."
                : "Changes apply to every user assigned to " + displayRole + " in both LOCAL and company-server deployments.");
        rebuildCopyRoles();
        cmbTemplate.getSelectionModel().clearSelection();
        rebuildVisibleRows();
    }

    private void rebuildCopyRoles() {
        String current = cmbRole.getValue();
        List<String> choices = cmbRole.getItems().stream()
                .filter(role -> !role.equalsIgnoreCase(current) && !"ADMIN".equalsIgnoreCase(role))
                .toList();
        cmbCopyRole.getItems().setAll(choices);
        if (!choices.isEmpty()) cmbCopyRole.getSelectionModel().selectFirst();
    }

    private void setEditingEnabled(boolean enabled) {
        btnApplyTemplate.setDisable(!enabled);
        btnCopyRole.setDisable(!enabled);
        btnReset.setDisable(!enabled);
        btnSave.setDisable(!enabled);
        cmbTemplate.setDisable(!enabled);
        cmbCopyRole.setDisable(!enabled);
        headerToggles.values().forEach(cb -> cb.setDisable(!enabled));
        table.refresh();
    }

    private boolean canEdit() {
        String role = cmbRole.getValue();
        return role != null && !"ADMIN".equalsIgnoreCase(role);
    }

    private void rebuildVisibleRows() {
        String search = txtSearch == null || txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        boolean grantedOnly = chkGrantedOnly != null && chkGrantedOnly.isSelected();
        tableRows.clear();
        String currentCategory = null;
        for (ModuleRow row : modules) {
            if (!matches(row, search)) continue;
            if (grantedOnly && row.permissions().stream().noneMatch(p -> p.allowed().get())) continue;
            if (!Objects.equals(currentCategory, row.meta().category())) {
                currentCategory = row.meta().category();
                tableRows.add(MatrixRow.category(currentCategory));
            }
            tableRows.add(row);
        }
        updateSummaryAndPreview();
        table.refresh();
    }

    private boolean matches(ModuleRow row, String search) {
        if (search.isBlank()) return true;
        if (row.meta().label().toLowerCase(Locale.ROOT).contains(search) || row.meta().category().toLowerCase(Locale.ROOT).contains(search)) return true;
        return row.permissions().stream().anyMatch(p -> PermissionCatalog.actionLabel(p.action()).toLowerCase(Locale.ROOT).contains(search)
                || (p.description() != null && p.description().toLowerCase(Locale.ROOT).contains(search)));
    }

    private List<ModuleRow> visibleModules() {
        return tableRows.stream().filter(row -> row instanceof ModuleRow).map(row -> (ModuleRow) row).toList();
    }

    private void permissionsChanged() {
        if (loading) return;
        updateSummaryAndPreview();
        updateHeaderStates();
        table.refresh();
    }

    private void updateSummaryAndPreview() {
        long total = modules.stream().mapToLong(row -> row.permissions().size()).sum();
        long granted = modules.stream().flatMap(row -> row.permissions().stream()).filter(p -> p.allowed().get()).count();
        lblGrantedSummary.setText(granted + " of " + total + " granted");
        lblLegend.setText(visibleModules().size() + " modules shown  •  ✓ granted   ◩ partial   — not supported");
        updateHeaderStates();
        rebuildPreview();
    }

    private void updateHeaderStates() {
        updateHeader("*", visibleModules().stream().flatMap(row -> row.permissions().stream()).toList());
        for (String action : PermissionCatalog.COMMON_ACTIONS) {
            updateHeader(action, visibleModules().stream().map(row -> row.permission(action)).filter(Objects::nonNull).toList());
        }
    }

    private void updateHeader(String key, List<PermissionEntry> entries) {
        CheckBox cb = headerToggles.get(key);
        if (cb == null) return;
        if (entries.isEmpty()) {
            cb.setSelected(false); cb.setIndeterminate(false); cb.setDisable(true); return;
        }
        if (canEdit()) cb.setDisable(false);
        long allowed = entries.stream().filter(p -> p.allowed().get()).count();
        cb.setIndeterminate(allowed > 0 && allowed < entries.size());
        cb.setSelected(allowed == entries.size());
    }

    private void rebuildPreview() {
        previewContent.getChildren().clear();
        Map<String, List<ModuleRow>> byCategory = new LinkedHashMap<>();
        for (ModuleRow row : modules) {
            if (row.permissions().stream().anyMatch(p -> p.allowed().get())) byCategory.computeIfAbsent(row.meta().category(), key -> new ArrayList<>()).add(row);
        }
        if (byCategory.isEmpty()) {
            Label none = new Label("No permissions are currently granted.");
            none.getStyleClass().add("muted-label");
            previewContent.getChildren().add(none);
            lblPreviewTitle.setText("Effective Access Preview");
            return;
        }
        long grantedModules = byCategory.values().stream().mapToLong(List::size).sum();
        lblPreviewTitle.setText("Effective Access Preview • " + grantedModules + " modules");
        byCategory.forEach((category, rows) -> previewContent.getChildren().add(previewCategory(category, rows)));
    }

    private Node previewCategory(String category, List<ModuleRow> rows) {
        VBox box = new VBox(3);
        box.getStyleClass().add("permission-preview-group");
        Label title = new Label(category);
        title.getStyleClass().add("permission-preview-category");
        Set<String> actions = new LinkedHashSet<>();
        int granted = 0;
        int total = 0;
        for (ModuleRow row : rows) {
            for (PermissionEntry p : row.permissions()) {
                total++;
                if (p.allowed().get()) { granted++; actions.add(PermissionCatalog.actionLabel(p.action())); }
            }
        }
        String actionText = actions.stream().limit(5).reduce((a,b) -> a + ", " + b).orElse("View");
        if (actions.size() > 5) actionText += " +" + (actions.size() - 5);
        Label detail = new Label(actionText + "  •  " + granted + "/" + total);
        detail.setWrapText(true);
        detail.getStyleClass().add("permission-preview-detail");
        box.getChildren().addAll(title, detail);
        return box;
    }

    @FXML private void applyTemplate() {
        if (!canEdit()) return;
        PermissionCatalog.Template template = cmbTemplate.getValue();
        if (template == null) {
            ModernDialog.info(root, "Choose a template", "Permission Matrix", "Select a permission template first.");
            return;
        }
        loading = true;
        try {
            for (ModuleRow row : modules) for (PermissionEntry permission : row.permissions()) {
                permission.allowed().set(PermissionCatalog.templateAllows(template, row.meta().key(), permission.action()));
            }
        } finally {
            loading = false;
        }
        lblHint.setText(template.label() + " applied as unsaved changes. Review the matrix, then Save Permissions.");
        permissionsChanged();
    }

    @FXML private void copyFromRole() {
        if (!canEdit()) return;
        String sourceRole = cmbCopyRole.getValue();
        if (sourceRole == null || sourceRole.isBlank()) return;
        UiTaskExecutor.submitLatest(
                "permission-matrix-copy",
                () -> api.permissions(sourceRole),
                permissions -> {
                    Map<String, Boolean> source = new HashMap<>();
                    for (var p : permissions) source.put(permissionKey(p.module(), p.action()), p.allowed());
                    loading = true;
                    try {
                        for (ModuleRow row : modules) for (PermissionEntry p : row.permissions())
                            p.allowed().set(source.getOrDefault(permissionKey(row.meta().key(), p.action()), false));
                    } finally { loading = false; }
                    lblHint.setText("Copied " + sourceRole + " permissions as unsaved changes. Review before saving.");
                    permissionsChanged();
                },
                failure -> showError("Permissions could not be copied", asException(failure))
        );
    }

    @FXML private void save() {
        String role = cmbRole.getValue();
        if (!canEdit() || role == null) return;
        long users = roleUserCounts.getOrDefault(role.toUpperCase(Locale.ROOT), 0L);
        if (!ModernDialog.confirm(root, "Save Permissions", "Apply permission changes to " + role + "?",
                "This updates access for " + users + (users == 1 ? " user" : " users") + " assigned to this role. The same server-owned matrix is used in LOCAL and company-server modes.")) return;
        List<AdminApiClient.PermissionSave> changes = modules.stream().flatMap(row -> row.permissions().stream())
                .map(p -> new AdminApiClient.PermissionSave(p.id(), p.allowed().get())).toList();
        btnSave.setDisable(true);
        UiTaskExecutor.submitAction(
                "permission-matrix-save-" + role,
                () -> { api.savePermissions(role, changes); PermissionService.refresh(); return null; },
                ignored -> {
                    btnSave.setDisable(!canEdit());
                    ToastManager.success(root, "Permissions saved", role + " access has been updated.");
                    lblHint.setText("Permissions saved for " + role + ".");
                },
                failure -> { btnSave.setDisable(!canEdit()); showError("Permissions could not be saved", asException(failure)); }
        );
    }

    @FXML private void reset() { loadRole(cmbRole.getValue()); }
    @Override public void onScreenHidden() { UiTaskExecutor.cancelPrefix("permission-matrix-"); }
    @FXML private void back() { DashboardController.navigateFromChildPage("Role Management", "/fxml/pages/RoleManagement.fxml"); }

    private static Exception asException(Throwable failure) {
        if (failure instanceof Exception exception) return exception;
        return new RuntimeException(failure);
    }

    private void showError(String title, Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null) rootCause = rootCause.getCause();
        String message = rootCause.getMessage();
        ModernDialog.error(root, title, "Permission Matrix", message == null || message.isBlank() ? rootCause.getClass().getSimpleName() : message);
    }

    private static String permissionKey(String module, String action) {
        return PermissionCatalog.normalize(module) + "." + PermissionCatalog.normalize(action);
    }

    private static List<PermissionEntry> entryList(PermissionEntry entry) {
        return entry == null ? List.of() : List.of(entry);
    }

    private final class MatrixCheckCell extends TableCell<MatrixRow, MatrixRow> {
        private final CheckBox check = new CheckBox();
        private final ConsumerState targets;
        private final String tooltipPrefix;

        MatrixCheckCell(java.util.function.Function<ModuleRow, Collection<PermissionEntry>> resolver, String tooltipPrefix) {
            this.targets = resolver::apply;
            this.tooltipPrefix = tooltipPrefix;
            check.setAllowIndeterminate(false);
            check.getStyleClass().add("permission-matrix-check");
            setAlignment(Pos.CENTER);
            check.setOnAction(event -> {
                MatrixRow item = getItem();
                if (!(item instanceof ModuleRow row) || !canEdit()) return;
                Collection<PermissionEntry> entries = targets.resolve(row);
                if (entries.isEmpty()) return;
                boolean selected = check.isSelected();
                check.setIndeterminate(false);
                loading = true;
                entries.forEach(p -> p.allowed().set(selected));
                loading = false;
                permissionsChanged();
            });
        }

        @Override protected void updateItem(MatrixRow item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null || item.group()) { setGraphic(null); return; }
            ModuleRow row = (ModuleRow) item;
            Collection<PermissionEntry> entries = targets.resolve(row);
            if (entries.isEmpty()) {
                Label dash = new Label("—");
                dash.getStyleClass().add("permission-not-supported");
                setGraphic(dash);
                return;
            }
            long allowed = entries.stream().filter(p -> p.allowed().get()).count();
            check.setSelected(allowed == entries.size());
            check.setIndeterminate(allowed > 0 && allowed < entries.size());
            check.setDisable(!canEdit());
            check.setTooltip(new Tooltip(tooltipPrefix + " • " + row.meta().label()));
            setGraphic(check);
        }
    }

    private final class ModuleCell extends TableCell<MatrixRow, MatrixRow> {
        @Override protected void updateItem(MatrixRow item, boolean empty) {
            super.updateItem(item, empty);
            setGraphic(null); setText(null);
            if (empty || item == null) return;
            if (item.group()) {
                Label label = new Label(item.label());
                label.getStyleClass().add("permission-group-label");
                setGraphic(label);
                return;
            }
            ModuleRow row = (ModuleRow) item;
            Label label = new Label(row.meta().label());
            label.getStyleClass().add("permission-module-label");
            Node icon = IconFactory.compactIcon(row.meta().iconKey(), 15);
            HBox box = new HBox(8, icon, label);
            box.setAlignment(Pos.CENTER_LEFT);
            setGraphic(box);
        }
    }

    @FunctionalInterface private interface ConsumerState { Collection<PermissionEntry> resolve(ModuleRow row); }

    public sealed interface MatrixRow permits CategoryRow, ModuleRow {
        boolean group();
        String label();
        static MatrixRow category(String label) { return new CategoryRow(label); }
    }

    public record CategoryRow(String label) implements MatrixRow {
        @Override public boolean group() { return true; }
    }

    public static final class ModuleRow implements MatrixRow {
        private final PermissionCatalog.ModuleMeta meta;
        private final Map<String, PermissionEntry> permissions = new LinkedHashMap<>();
        ModuleRow(PermissionCatalog.ModuleMeta meta) { this.meta = meta; }
        void add(PermissionEntry entry) { permissions.put(entry.action(), entry); }
        PermissionEntry permission(String action) { return permissions.get(PermissionCatalog.normalize(action)); }
        Collection<PermissionEntry> permissions() { return permissions.values(); }
        PermissionCatalog.ModuleMeta meta() { return meta; }
        @Override public boolean group() { return false; }
        @Override public String label() { return meta.label(); }
    }

    public record PermissionEntry(long id, String module, String action, String description, BooleanProperty allowed) {}
}
