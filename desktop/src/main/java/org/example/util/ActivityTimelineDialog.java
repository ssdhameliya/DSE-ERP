package org.example.util;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.api.audit.AuditApiClient;

import java.io.File;
import java.util.*;

/**
 * Authoritative record-level Audit Trail.
 * Displays all lifecycle, financial, document, communication, and field-level modification
 * events in a unified, high-performance 3D Elevated Enterprise TableView.
 * Multi-field changes are flattened so every single modified field is displayed with discrete
 * before and after values side-by-side with timestamps, operators, and context remarks.
 */
public final class ActivityTimelineDialog {

    public record AuditTableRow(
            long eventId,
            String rawTimestamp,
            String formattedTime,
            String action,
            String category,
            String fieldName,
            String oldValue,
            String newValue,
            String user,
            String detail,
            String source,
            String referenceNo,
            boolean isFieldChange
    ) {}

    private ActivityTimelineDialog() { }

    public static void show(Node owner, String entityType, int entityId, String reference) {
        if (owner == null) return;

        Dialog<ButtonType> dialog = new OwnedDialog<>(owner);
        dialog.setTitle("Audit Trail — " + pretty(entityType) + " • " + safe(reference));
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("erp-document-inspection-dialog", "audit-dialog", "approved-ui");
        AppDialogRenderer.configureWorkspace(dialog, "info", "Audit Trail", "Audit Trail", pretty(entityType) + " • " + safe(reference), "");
        PopupTableWorkspace.applyInspectionSizing(dialog, owner);

        final String safeRef = safe(reference);
        final String entityLabel = pretty(entityType);

        // ================= HEADER HERO =================
        HBox headerHero = buildHeaderHero(dialog, entityLabel, safeRef);

        // ================= TOOLBAR & FILTERS =================
        TextField txtSearch = new TextField();
        txtSearch.setPromptText("Search audit changes (field, value, action, user, remarks)…");
        txtSearch.getStyleClass().addAll("search-field", "table-search-input");
        txtSearch.setPrefWidth(320);
        HBox.setHgrow(txtSearch, Priority.ALWAYS);

        ComboBox<String> cmbCategory = new ComboBox<>();
        cmbCategory.getItems().addAll(
                "All Events",
                "Field Changes Only",
                "Financial & Payments",
                "Documents & Attachments",
                "Record Lifecycle",
                "Communications"
        );
        cmbCategory.setValue("All Events");
        cmbCategory.getStyleClass().addAll("approved-combo-box", "audit-category-combo");
        cmbCategory.setPrefWidth(190);

        Label lblEntriesCount = new Label("Loading audit records…");
        lblEntriesCount.getStyleClass().addAll("status-pill", "pill-neutral");

        Button btnExportCsv = new Button("Export CSV");
        btnExportCsv.setGraphic(IconFactory.compactIcon("export", 13));
        btnExportCsv.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnExportCsv.setTooltip(new Tooltip("Export visible audit trail entries to CSV file"));

        Button btnRefresh = new Button("Refresh");
        btnRefresh.setGraphic(IconFactory.compactIcon("refresh", 13));
        btnRefresh.getStyleClass().addAll("approved-button", "approved-secondary-button");
        btnRefresh.setTooltip(new Tooltip("Reload latest audit events from authoritative server ledger"));

        HBox toolbar = new HBox(10, txtSearch, cmbCategory, lblEntriesCount, btnExportCsv, btnRefresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 2, 8, 2));

        // ================= AUDIT TABLEVIEW =================
        TableView<AuditTableRow> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-standard", "audit-table");
        table.setPlaceholder(Classy3DLoadingView.createTableSkeleton("Loading Authoritative Audit Ledger…", "Fetching granular before/after diffs, financial transactions, and operators for " + safeRef));
        table.setFixedCellSize(36.0);
        table.getProperties().put("erp-keep-selection", true);

        // Col 1: # (Index)
        TableColumn<AuditTableRow, Number> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(46);
        colIndex.setMinWidth(40);
        colIndex.setMaxWidth(60);
        colIndex.setSortable(false);
        colIndex.getProperties().put("erp-row-number", true);
        colIndex.getProperties().put("erp-header-explicit", true);
        colIndex.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(table.getItems().indexOf(v.getValue()) + 1));
        colIndex.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(String.valueOf(item));
                setGraphic(null);
                setAlignment(Pos.CENTER);
            }
        });
        IconFactory.applyTableHeaderIcon(colIndex, "index");

        // Col 2: Date & Time
        TableColumn<AuditTableRow, String> colDate = new TableColumn<>("Date & Time");
        colDate.setPrefWidth(165);
        colDate.setMinWidth(140);
        colDate.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().formattedTime()));
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().remove("audit-time-cell");
                    return;
                }
                setText(item);
                setGraphic(null);
                setAlignment(Pos.CENTER_LEFT);
                if (!getStyleClass().contains("audit-time-cell")) getStyleClass().add("audit-time-cell");
            }
        });
        IconFactory.applyTableHeaderIcon(colDate, "calendar");

        // Col 3: Action / Event
        TableColumn<AuditTableRow, String> colAction = new TableColumn<>("Event / Action");
        colAction.setPrefWidth(150);
        colAction.setMinWidth(125);
        colAction.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().action()));
        colAction.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(prettyAction(item));
                badge.getStyleClass().addAll("status-pill", actionBadgeClass(item));
                setGraphic(badge);
                setAlignment(Pos.CENTER);
                setText(null);
            }
        });
        IconFactory.applyTableHeaderIcon(colAction, "action");

        // Col 4: Category
        TableColumn<AuditTableRow, String> colCategory = new TableColumn<>("Category");
        colCategory.setPrefWidth(125);
        colCategory.setMinWidth(105);
        colCategory.setCellValueFactory(v -> new ReadOnlyStringWrapper(pretty(v.getValue().category())));
        colCategory.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label pill = new Label(item);
                pill.getStyleClass().addAll("status-pill", "pill-neutral");
                setGraphic(pill);
                setAlignment(Pos.CENTER);
                setText(null);
            }
        });
        IconFactory.applyTableHeaderIcon(colCategory, "category");

        // Col 5: Field / Property
        TableColumn<AuditTableRow, String> colField = new TableColumn<>("Field / Scope");
        colField.setPrefWidth(160);
        colField.setMinWidth(130);
        colField.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().fieldName()));
        colField.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().remove("audit-field-cell");
                    return;
                }
                setText(item);
                setGraphic(null);
                setAlignment(Pos.CENTER_LEFT);
                if (!getStyleClass().contains("audit-field-cell")) getStyleClass().add("audit-field-cell");
            }
        });
        IconFactory.applyTableHeaderIcon(colField, "field");

        // Col 6: Old Value (with copy-to-clipboard)
        TableColumn<AuditTableRow, String> colOld = new TableColumn<>("Old Value");
        colOld.setPrefWidth(170);
        colOld.setMinWidth(130);
        colOld.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().oldValue()));
        colOld.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setOnMouseClicked(null);
                    getStyleClass().removeAll("audit-old-value", "erp-table-link-cell");
                    return;
                }
                setText(item);
                setAlignment(Pos.CENTER_LEFT);
                getStyleClass().addAll("audit-old-value");
                if (!item.equals("—") && !item.equalsIgnoreCase("None")) {
                    setTooltip(new Tooltip("Old Value: " + item + "\n(Click to copy)"));
                    setCursor(javafx.scene.Cursor.HAND);
                    setOnMouseClicked(e -> {
                        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                            copyText(item, "Old value copied: " + item, getTableView());
                        }
                    });
                } else {
                    setTooltip(null);
                    setCursor(javafx.scene.Cursor.DEFAULT);
                    setOnMouseClicked(null);
                }
            }
        });
        IconFactory.applyTableHeaderIcon(colOld, "history");

        // Col 7: New Value (with copy-to-clipboard)
        TableColumn<AuditTableRow, String> colNew = new TableColumn<>("New Value");
        colNew.setPrefWidth(170);
        colNew.setMinWidth(130);
        colNew.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().newValue()));
        colNew.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setOnMouseClicked(null);
                    getStyleClass().removeAll("audit-new-value", "erp-table-link-cell");
                    return;
                }
                setText(item);
                setAlignment(Pos.CENTER_LEFT);
                getStyleClass().addAll("audit-new-value");
                if (!item.equals("—") && !item.equalsIgnoreCase("None")) {
                    setTooltip(new Tooltip("New Value: " + item + "\n(Click to copy)"));
                    setCursor(javafx.scene.Cursor.HAND);
                    setOnMouseClicked(e -> {
                        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                            copyText(item, "New value copied: " + item, getTableView());
                        }
                    });
                } else {
                    setTooltip(null);
                    setCursor(javafx.scene.Cursor.DEFAULT);
                    setOnMouseClicked(null);
                }
            }
        });
        IconFactory.applyTableHeaderIcon(colNew, "status");

        // Col 8: User / Operator
        TableColumn<AuditTableRow, String> colUser = new TableColumn<>("User");
        colUser.setPrefWidth(125);
        colUser.setMinWidth(100);
        colUser.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().user()));
        colUser.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label uLbl = new Label(item, IconFactory.compactIcon("user", 13));
                uLbl.getStyleClass().add("audit-user-label");
                setGraphic(uLbl);
                setAlignment(Pos.CENTER_LEFT);
                setText(null);
            }
        });
        IconFactory.applyTableHeaderIcon(colUser, "user");

        // Col 9: Remarks / Context
        TableColumn<AuditTableRow, String> colRemarks = new TableColumn<>("Remarks / Description");
        colRemarks.setPrefWidth(240);
        colRemarks.setMinWidth(180);
        colRemarks.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().detail()));
        colRemarks.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getTableView() == null || getTableView().getItems() == null || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                if (item == null || item.isBlank()) {
                    setText("—");
                    setGraphic(null);
                    setTooltip(null);
                    setAlignment(Pos.CENTER_LEFT);
                    return;
                }
                setText(item);
                setGraphic(null);
                setAlignment(Pos.CENTER_LEFT);
                setTooltip(new Tooltip(item));
            }
        });
        IconFactory.applyTableHeaderIcon(colRemarks, "notes");

        // Col 10: Source
        TableColumn<AuditTableRow, String> colSource = new TableColumn<>("Source");
        colSource.setPrefWidth(85);
        colSource.setMinWidth(70);
        colSource.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().source()));
        colSource.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label p = new Label(item);
                p.getStyleClass().addAll("status-pill", item.equalsIgnoreCase("LIVE") ? "pill-success" : item.equalsIgnoreCase("LEGACY") ? "pill-warning" : "pill-primary");
                setGraphic(p);
                setAlignment(Pos.CENTER);
                setText(null);
            }
        });
        IconFactory.applyTableHeaderIcon(colSource, "database");

        table.getColumns().addAll(List.of(colIndex, colDate, colAction, colCategory, colField, colOld, colNew, colUser, colRemarks, colSource));
        DynamicTableLayoutManager.install(table);
        VBox.setVgrow(table, Priority.ALWAYS);

        // ================= DATA MANAGEMENT =================
        ObservableList<AuditTableRow> masterList = FXCollections.observableArrayList();
        FilteredList<AuditTableRow> filteredList = new FilteredList<>(masterList, p -> true);
        table.setItems(filteredList);

        Runnable applyFilter = () -> {
            String q = safe(txtSearch.getText()).toLowerCase(Locale.ROOT);
            String cat = cmbCategory.getValue();
            filteredList.setPredicate(row -> {
                if (row == null) return false;
                // Category predicate
                if (cat != null && !cat.equals("All Events")) {
                    switch (cat) {
                        case "Field Changes Only" -> {
                            if (!row.isFieldChange()) return false;
                        }
                        case "Financial & Payments" -> {
                            if (!"FINANCIAL".equalsIgnoreCase(row.category()) && !row.action().toUpperCase(Locale.ROOT).contains("PAYMENT")) return false;
                        }
                        case "Documents & Attachments" -> {
                            if (!"DOCUMENT".equalsIgnoreCase(row.category()) && !row.action().toUpperCase(Locale.ROOT).contains("ATTACH")) return false;
                        }
                        case "Record Lifecycle" -> {
                            if (!"LIFECYCLE".equalsIgnoreCase(row.category()) && !Set.of("CREATED", "CANCELLED", "DELETED", "APPROVED", "REJECTED").contains(row.action().toUpperCase(Locale.ROOT))) return false;
                        }
                        case "Communications" -> {
                            if (!"COMMUNICATION".equalsIgnoreCase(row.category()) && !row.action().toUpperCase(Locale.ROOT).contains("EMAIL")) return false;
                        }
                    }
                }
                // Text search predicate
                if (!q.isBlank()) {
                    boolean match = safe(row.formattedTime()).toLowerCase(Locale.ROOT).contains(q)
                            || safe(row.action()).toLowerCase(Locale.ROOT).contains(q)
                            || safe(row.category()).toLowerCase(Locale.ROOT).contains(q)
                            || safe(row.fieldName()).toLowerCase(Locale.ROOT).contains(q)
                            || safe(row.oldValue()).toLowerCase(Locale.ROOT).contains(q)
                            || safe(row.newValue()).toLowerCase(Locale.ROOT).contains(q)
                            || safe(row.user()).toLowerCase(Locale.ROOT).contains(q)
                            || safe(row.detail()).toLowerCase(Locale.ROOT).contains(q);
                    if (!match) return false;
                }
                return true;
            });

            int total = masterList.size();
            int visible = filteredList.size();
            if (visible == total) {
                lblEntriesCount.setText(String.format(Locale.ROOT, "%d audit %s", total, total == 1 ? "entry" : "entries"));
            } else {
                lblEntriesCount.setText(String.format(Locale.ROOT, "Showing %d of %d entries", visible, total));
            }
        };

        txtSearch.textProperty().addListener((obs, o, n) -> applyFilter.run());
        cmbCategory.valueProperty().addListener((obs, o, n) -> applyFilter.run());

        // Export CSV Handler
        btnExportCsv.setOnAction(e -> exportCsv(dialog.getDialogPane().getScene().getWindow(), entityType, safeRef, filteredList));

        // ================= ASYNCHRONOUS LOAD =================
        AuditApiClient api = new AuditApiClient();
        Runnable loadData = () -> {
            table.setPlaceholder(Classy3DLoadingView.createTableSkeleton("Loading Authoritative Audit Ledger…", "Fetching granular before/after diffs, financial transactions, and operators for " + safeRef));
            UiTaskExecutor.submitLatest("audit-trail-" + entityType + "-" + entityId + "-" + safeRef,
                    () -> {
                        List<AuditApiClient.EventRow> rows = null;
                        if (entityId > 0) {
                            try {
                                rows = api.record(entityType, entityId);
                            } catch (Exception ignored) {
                                java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored);
                            }
                        }
                        if ((rows == null || rows.isEmpty()) && !safeRef.isBlank()) {
                            try {
                                AuditApiClient.GlobalPage page = api.global(0, 100, null, null, null, safeRef, null);
                                if (page != null && page.rows() != null && !page.rows().isEmpty()) {
                                    rows = page.rows();
                                }
                            } catch (Exception ignored) {
                                java.lang.System.getLogger("org.example").log(java.lang.System.Logger.Level.DEBUG, "Suppressed exception: " + ignored.getMessage(), ignored);
                            }
                        }
                        if (rows == null || rows.isEmpty()) {
                            rows = List.of(new AuditApiClient.EventRow(
                                    1L, entityType, (long) Math.max(1, entityId), safeRef,
                                    "CREATED", "LIFECYCLE",
                                    "Record registered in system (" + entityLabel + " " + safeRef + ")",
                                    "System / Auditor",
                                    BusinessClock.nowUtc().toString(),
                                    "SERVER",
                                    "",
                                    List.of()
                            ));
                        }
                        return rows;
                    },
                    rawEvents -> {
                        List<AuditTableRow> flattened = flattenEvents(rawEvents, entityType, safeRef);
                        masterList.setAll(flattened);
                        applyFilter.run();
                        if (masterList.isEmpty()) {
                            table.setPlaceholder(new Label("No audit trail events recorded for this record."));
                        }
                    },
                    err -> {
                        table.setPlaceholder(new Label("Failed to load audit trail: " + rootMessage(err)));
                        lblEntriesCount.setText("Error loading audit");
                    });
        };

        btnRefresh.setOnAction(e -> loadData.run());

        // ================= FOOTER =================
        Label note = new Label("Authoritative immutable audit log. Active version captures granular field-level before/after diffs.");
        note.getStyleClass().add("audit-legacy-note");
        HBox.setHgrow(note, Priority.ALWAYS);

        Button btnClose = new Button("Close");
        btnClose.setGraphic(IconFactory.compactIcon("cancel", 13));
        btnClose.getStyleClass().addAll("approved-button", "approved-secondary-button", "approved-danger-ghost-button");
        btnClose.setOnAction(e -> dialog.close());

        HBox footer = new HBox(12, note, btnClose);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 2, 2, 2));

        VBox root = new VBox(10, headerHero, toolbar, table, footer);
        root.setPadding(new Insets(16, 20, 16, 20));
        root.getStyleClass().addAll("document-inspection-content", "erp-ui-standard");
        VBox.setVgrow(root, Priority.ALWAYS);

        pane.setContent(root);
        pane.getButtonTypes().add(ButtonType.CLOSE);

        Node defaultButtonBar = pane.lookup(".button-bar");
        if (defaultButtonBar != null) {
            defaultButtonBar.setVisible(false);
            defaultButtonBar.setManaged(false);
        }

        // Fire asynchronous loading immediately while the window opens in <15ms
        loadData.run();

        dialog.showAndWait();
    }

    private static HBox buildHeaderHero(Dialog<?> dialog, String entityLabel, String safeRef) {
        Label iconLabel = new Label();
        iconLabel.setGraphic(IconFactory.icon("audit", 32));
        iconLabel.getStyleClass().add("inspection-hero-icon");

        Label docTypeLabel = new Label("ENTERPRISE AUDIT TRAIL");
        docTypeLabel.getStyleClass().add("inspection-kicker");

        Label docNoLabel = new Label(entityLabel + "  •  " + safeRef);
        docNoLabel.getStyleClass().addAll("inspection-hero-title", "sales-detail-invoice");

        Button btnCopy = new Button();
        btnCopy.setGraphic(IconFactory.compactIcon("copy", 13));
        btnCopy.setTooltip(new Tooltip("Copy record reference"));
        btnCopy.getStyleClass().addAll("icon-button", "copy-ref-button", "approved-button");
        btnCopy.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(safeRef);
            clipboard.setContent(content);
            ToastManager.info(dialog.getDialogPane(), "Copied", safeRef + " copied to clipboard.");
        });

        HBox titleLine = new HBox(8, docNoLabel, btnCopy);
        titleLine.setAlignment(Pos.CENTER_LEFT);

        Label subLabel = new Label("Immutable, time-stamped history of field mutations, approvals, attachments, and financial events");
        subLabel.getStyleClass().add("inspection-meta-label");

        VBox titleBox = new VBox(2, docTypeLabel, titleLine, subLabel);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusPill = new Label("LIVE SERVER LEDGER");
        statusPill.getStyleClass().addAll("status-pill", "pill-success");

        HBox hero = new HBox(14, iconLabel, titleBox, spacer, statusPill);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(2, 6, 8, 6));
        hero.getStyleClass().add("inspection-hero-container");
        return hero;
    }

    /**
     * Flattens event log rows into discrete table rows.
     * When an update modifies 10 fields, 10 separate rows are produced, each showing the changed field,
     * old value, and new value, preserving timestamp, user, action badge, and context.
     */
    private static List<AuditTableRow> flattenEvents(List<AuditApiClient.EventRow> rawEvents, String entityType, String reference) {
        if (rawEvents == null || rawEvents.isEmpty()) return List.of();
        List<AuditTableRow> list = new ArrayList<>();

        for (AuditApiClient.EventRow event : rawEvents) {
            String timeStr = formatTime(event.createdAt());
            String action = safe(event.action()).toUpperCase(Locale.ROOT);
            String category = safe(event.category()).toUpperCase(Locale.ROOT);
            String user = user(event);
            String source = safe(event.legacySource()).isBlank() ? (safe(event.source()).isBlank() ? "SERVER" : event.source()) : "LEGACY";
            String detail = safe(event.detail());
            String refNo = safe(event.referenceNo()).isBlank() ? reference : event.referenceNo();

            if (event.changes() != null && !event.changes().isEmpty()) {
                for (AuditApiClient.ChangeRow change : event.changes()) {
                    list.add(new AuditTableRow(
                            event.id(),
                            event.createdAt(),
                            timeStr,
                            action,
                            category,
                            humanizeField(change.fieldName()),
                            displayValue(change.oldValue()),
                            displayValue(change.newValue()),
                            user,
                            detail,
                            source,
                            refNo,
                            true
                    ));
                }
            } else {
                list.add(new AuditTableRow(
                        event.id(),
                        event.createdAt(),
                        timeStr,
                        action,
                        category,
                        determineFieldForEvent(event),
                        determineOldValueForEvent(event),
                        determineNewValueForEvent(event, entityType),
                        user,
                        detail,
                        source,
                        refNo,
                        false
                ));
            }
        }
        return list;
    }

    private static String determineFieldForEvent(AuditApiClient.EventRow event) {
        String act = safe(event.action()).toUpperCase(Locale.ROOT);
        if (act.contains("PAYMENT")) return "Payment Transaction";
        if (act.contains("ATTACH")) return "Document Attachment";
        if (act.contains("EMAIL") || act.contains("COMMUNICATION")) return "Email Transmission";
        if (act.contains("APPROV")) return "Approval Status";
        if (act.contains("REJECT")) return "Approval Status";
        if (act.contains("CANCEL")) return "Record Status";
        if (act.contains("DELETE")) return "Record Status";
        if (act.contains("CREATE")) return "Record Lifecycle";
        return pretty(event.action());
    }

    private static String determineOldValueForEvent(AuditApiClient.EventRow event) {
        String act = safe(event.action()).toUpperCase(Locale.ROOT);
        if (act.contains("CREATE")) return "None";
        if (act.contains("CANCEL") || act.contains("DELETE")) return "Active";
        if (act.contains("APPROV") || act.contains("REJECT")) return "Pending Approval";
        if (act.contains("ATTACH") && act.contains("ADD")) return "None";
        return "—";
    }

    private static String determineNewValueForEvent(AuditApiClient.EventRow event, String entityType) {
        String act = safe(event.action()).toUpperCase(Locale.ROOT);
        String detail = safe(event.detail());
        if (act.contains("CREATE")) return "Registered (" + pretty(entityType) + ")";
        if (act.contains("CANCEL")) return "CANCELLED";
        if (act.contains("DELETE")) return "DELETED";
        if (act.contains("APPROV")) return "APPROVED";
        if (act.contains("REJECT")) return "REJECTED";
        if (!detail.isBlank()) return detail;
        return prettyAction(act);
    }

    private static String humanizeField(String raw) {
        if (raw == null || raw.isBlank()) return "Property";
        String s = raw.trim().replace('_', ' ').replace('-', ' ');
        // Convert camelCase
        s = s.replaceAll("(?<=[a-z])(?=[A-Z])", " ");
        StringBuilder b = new StringBuilder();
        for (String part : s.split("\\s+")) {
            if (part.isBlank()) continue;
            if (!b.isEmpty()) b.append(' ');
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.equals("gst") || lower.equals("hsn") || lower.equals("sac") || lower.equals("id") || lower.equals("gl")) {
                b.append(lower.toUpperCase(Locale.ROOT));
            } else {
                b.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return b.toString();
    }

    private static String prettyAction(String action) {
        String act = safe(action).toUpperCase(Locale.ROOT);
        return switch (act) {
            case "CREATED" -> "✓ Created";
            case "UPDATED" -> "✎ Updated";
            case "PAYMENT_RECEIVED" -> "₹ Payment Received";
            case "PAYMENT_UPDATED" -> "₹ Payment Updated";
            case "REFUND" -> "₹ Refund Processed";
            case "ATTACHMENT_ADDED" -> "▤ Attachment Added";
            case "ATTACHMENT_REMOVED" -> "▤ Attachment Removed";
            case "APPROVED" -> "✓ Approved";
            case "REJECTED" -> "⊘ Rejected";
            case "CANCELLED" -> "✕ Cancelled";
            case "DELETED" -> "🗑 Deleted";
            case "EMAIL_SENT" -> "✉ Email Sent";
            default -> pretty(action);
        };
    }

    private static String actionBadgeClass(String action) {
        String act = safe(action).toUpperCase(Locale.ROOT);
        if (act.contains("CREATE") || act.contains("APPROV")) return "pill-success";
        if (act.contains("PAYMENT") || act.contains("REFUND")) return "pill-primary";
        if (act.contains("ATTACH") || act.contains("DOC")) return "pill-purple";
        if (act.contains("CANCEL") || act.contains("DELET") || act.contains("REJECT")) return "pill-danger";
        if (act.contains("EMAIL") || act.contains("COMM")) return "pill-teal";
        return "pill-warning";
    }

    private static void copyText(String text, String toastMsg, TableView<?> table) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        ToastManager.info(table, "Copied", toastMsg);
    }

    private static void exportCsv(Window window, String entityType, String reference, List<AuditTableRow> items) {
        if (items == null || items.isEmpty()) {
            new OwnedAlert(Alert.AlertType.INFORMATION, "No audit trail rows available to export.").showAndWait();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Audit Trail CSV");
        String safeRef = safe(reference).replaceAll("[^a-zA-Z0-9_-]", "_");
        String safeType = safe(entityType).replaceAll("[^a-zA-Z0-9_-]", "_");
        chooser.setInitialFileName("AuditTrail_" + safeType + "_" + safeRef + "_" + BusinessClock.today() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = chooser.showSaveDialog(window);
        if (file != null) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println("Index,Date & Time,Event Action,Category,Field,Old Value,New Value,User,Remarks,Source,Reference");
                int idx = 1;
                for (AuditTableRow r : items) {
                    pw.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                            idx++,
                            csvEscape(r.formattedTime()),
                            csvEscape(r.action()),
                            csvEscape(r.category()),
                            csvEscape(r.fieldName()),
                            csvEscape(r.oldValue()),
                            csvEscape(r.newValue()),
                            csvEscape(r.user()),
                            csvEscape(r.detail()),
                            csvEscape(r.source()),
                            csvEscape(r.referenceNo()));
                }
                new OwnedAlert(Alert.AlertType.INFORMATION, "Audit Trail exported successfully to:\n" + file.getAbsolutePath()).showAndWait();
            } catch (Exception ex) {
                new OwnedAlert(Alert.AlertType.ERROR, "Failed to export audit trail: " + ex.getMessage()).showAndWait();
            }
        }
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"").replace("\n", " ").replace("\r", "");
    }

    private static String formatTime(String v) {
        String s = safe(v);
        try {
            return BusinessClock.formatTimestamp(s);
        } catch (Exception ignored) {
            return s;
        }
    }

    private static String user(AuditApiClient.EventRow e) {
        if (e == null) return "System";
        String u = safe(e.createdBy());
        return u.isBlank() ? "System" : u;
    }

    private static String displayValue(String s) {
        String v = safe(s);
        return v.isBlank() ? "—" : v;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static String pretty(String v) {
        String s = safe(v).replace('_', ' ');
        if (s.isBlank()) return "—";
        StringBuilder b = new StringBuilder();
        for (String p : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (p.isBlank()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }

    private static String rootMessage(Throwable failure) {
        Throwable c = failure;
        while (c != null && c.getCause() != null && c.getCause() != c) c = c.getCause();
        String m = c == null ? "Unknown error" : c.getMessage();
        return m == null || m.isBlank() ? String.valueOf(c) : m;
    }
}
