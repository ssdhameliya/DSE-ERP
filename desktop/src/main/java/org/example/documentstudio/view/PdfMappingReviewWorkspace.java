package org.example.documentstudio.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.documentstudio.model.TemplateFieldDefinition;
import org.example.documentstudio.service.PdfMappingReviewSession;
import org.example.documentstudio.service.PdfMappingReviewSession.Entry;
import org.example.documentstudio.service.PdfMappingReviewSession.Section;
import org.example.documentstudio.service.PdfMappingReviewSession.NavigationItem;
import org.example.util.SemanticIconManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Content-only mapping review workspace hosted by the centralized DSE dialog system.
 * It owns no Stage, modal shell, stylesheet or theme logic.
 */
public final class PdfMappingReviewWorkspace extends BorderPane {
    private final PdfMappingReviewSession session;
    private final ListView<NavigationItem> sections = new ListView<>();
    private final VBox rows = new VBox(12);
    private final Label summary = new Label();
    private final Label sectionTitle = new Label();
    private final Label sectionHelp = new Label();

    public PdfMappingReviewWorkspace(PdfMappingReviewSession session, Section initialSection) {
        this.session = Objects.requireNonNull(session);
        getStyleClass().add("dse-workspace-mapping-review");
        setMinSize(980, 590);
        setPrefSize(1160, 680);

        sections.setItems(FXCollections.observableArrayList(session.navigationItems()));
        sections.getStyleClass().add("dse-workspace-navigation");
        sections.setPrefWidth(220);
        sections.setMinWidth(205);
        sections.setMaxWidth(220);
        sections.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(NavigationItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label title = new Label(item.label());
                title.getStyleClass().add("dse-workspace-nav-title");
                Label count = new Label(sectionCount(item));
                count.getStyleClass().add("dse-workspace-nav-count");
                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
                Node icon = SemanticIconManager.compact(item.icon(), 16);
                HBox line = new HBox(9, icon, title, spacer, count);
                line.setAlignment(Pos.CENTER_LEFT);
                setGraphic(line); setText(null);
            }
        });
        sections.getSelectionModel().selectedItemProperty().addListener((obs,o,n) -> { if (n != null) renderSection(n); });

        VBox nav = new VBox(9, navHeading(), sections);
        nav.setMinWidth(220);
        nav.setPrefWidth(230);
        nav.setMaxWidth(230);
        nav.getStyleClass().add("dse-workspace-nav-card");
        VBox.setVgrow(sections, Priority.ALWAYS);
        setLeft(nav);
        BorderPane.setMargin(nav, new Insets(0, 16, 0, 0));

        HBox actions = new HBox(9);
        Button confirmHigh = action("Confirm All High Confidence", "complete", () -> { session.confirmAllHighConfidence(); refresh(); });
        confirmHigh.setMinWidth(205); confirmHigh.setPrefWidth(205);
        Button resetAuto = action("Reset to Auto Detection", "reset", () -> { session.resetAllAuto(); refresh(); });
        resetAuto.setMinWidth(185); resetAuto.setPrefWidth(185);
        actions.getChildren().addAll(confirmHigh, resetAuto);
        actions.setAlignment(Pos.CENTER_RIGHT);

        sectionTitle.getStyleClass().add("dse-workspace-section-title");
        sectionHelp.setWrapText(true); sectionHelp.getStyleClass().add("dse-workspace-section-help");
        summary.getStyleClass().add("dse-workspace-summary");
        VBox titleBox = new VBox(3, sectionTitle, sectionHelp);
        titleBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        VBox heading = new VBox(8, titleBox, actions);
        actions.setMaxWidth(Double.MAX_VALUE);

        ScrollPane scroller = new ScrollPane(rows);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.getStyleClass().add("dse-workspace-scroll");
        rows.setFillWidth(true);
        VBox center = new VBox(12, heading, summary, scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        setCenter(center);

        NavigationItem start = initialNavigation(initialSection);
        sections.getSelectionModel().select(start);
        renderSection(start);
    }

    public Section selectedSection() {
        NavigationItem item = sections.getSelectionModel().getSelectedItem();
        return item == null ? Section.HEADER : item.section();
    }

    private Node navHeading() {
        Label kicker = new Label("MAPPING SECTIONS");
        kicker.getStyleClass().add("dse-workspace-nav-kicker");
        Label help = new Label("Review what PDF Studio detected. Your confirmed/manual choices are preserved when detection runs again.");
        help.setWrapText(true); help.getStyleClass().add("dse-workspace-nav-help");
        return new VBox(5, kicker, help);
    }

    private void refresh() {
        NavigationItem selected = sections.getSelectionModel().getSelectedItem();
        String selectedId = selected == null ? "" : selected.id();
        sections.setItems(FXCollections.observableArrayList(session.navigationItems()));
        NavigationItem restored = sections.getItems().stream().filter(i -> i.id().equals(selectedId)).findFirst().orElse(firstUsefulSection());
        sections.getSelectionModel().select(restored);
        sections.refresh();
        renderSection(restored);
    }

    private void renderSection(NavigationItem item) {
        if (item == null) item = firstUsefulSection();
        Section section = item.section();
        rows.getChildren().clear();
        sectionTitle.setText(item.label());
        sectionHelp.setText(item.dynamic()
                ? "Detected source block discovered from PDF geometry/context. Review its values with the same centralized controls; no block-specific controller is required."
                : helpFor(section));
        summary.setText("Auto " + session.autoCount() + "   •   Confirmed " + session.confirmedCount()
                + "   •   Manual override " + session.overrideCount() + "   •   Need review " + session.reviewRequired());
        List<Entry> entries = session.entries(item);
        if (entries.isEmpty()) {
            Label empty = new Label("No detected or mapped fields in this section yet. Use Auto Map or click a printed PDF value and map it manually.");
            empty.setWrapText(true); empty.getStyleClass().add("dse-workspace-empty");
            rows.getChildren().add(empty); return;
        }

        Map<String,List<Entry>> groups = entries.stream().collect(Collectors.groupingBy(
                e -> (e.blockId().isBlank() ? e.section().name() : e.blockId()) + "\u0000" + (e.blockLabel().isBlank() ? e.section().label() : e.blockLabel()),
                LinkedHashMap::new, Collectors.toList()));
        for (List<Entry> group : groups.values()) rows.getChildren().add(groupCard(section, group));
    }

    private Node groupCard(Section section, List<Entry> entries) {
        String blockLabel = entries.stream().map(Entry::blockLabel).filter(v -> v != null && !v.isBlank()).findFirst()
                .orElse(section.label().toUpperCase(Locale.ROOT));
        Label title = new Label(blockLabel.toUpperCase(Locale.ROOT));
        title.getStyleClass().add("dse-workspace-card-title");
        Label note = new Label(groupHelp(section));
        note.setWrapText(true); note.getStyleClass().add("dse-workspace-section-help");
        VBox body = new VBox(9);
        for (Entry entry : entries) {
            if (entry.kind() == PdfMappingReviewSession.Kind.FINANCIAL) body.getChildren().add(financialCard(entry));
            else if (entry.kind() == PdfMappingReviewSession.Kind.FINANCIAL_ROLE) body.getChildren().add(financialRoleRow(entry));
            else body.getChildren().add(mappingRow(entry));
        }
        VBox card = new VBox(8, title, note, body);
        card.getStyleClass().add("dse-workspace-section-card");
        return card;
    }

    private Node mappingRow(Entry entry) {
        Label source = new Label(entry.sourceLabel().isBlank() ? "Detected PDF value" : entry.sourceLabel());
        source.setWrapText(true); source.getStyleClass().add("dse-workspace-source-label");
        VBox sourceBox = new VBox(4, source);
        sourceBox.setMinWidth(215); sourceBox.setPrefWidth(230); sourceBox.setMaxWidth(250);
        if (!entry.sourceValue().isBlank() && !entry.sourceValue().equalsIgnoreCase(entry.sourceLabel())) {
            Label sourceValue = new Label("Source: " + entry.sourceValue());
            sourceValue.setWrapText(true); sourceValue.getStyleClass().add("dse-workspace-secondary");
            sourceBox.getChildren().add(sourceValue);
        }
        sourceBox.getChildren().add(autoSuggestion(entry));

        ComboBox<TemplateFieldDefinition> field = new ComboBox<>();
        List<TemplateFieldDefinition> options = session.fieldOptions(entry);
        field.setItems(FXCollections.observableArrayList(options));
        field.setEditable(false); field.setMinWidth(270); field.setPrefWidth(300); field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        selectField(field, entry.fieldKey());
        field.valueProperty().addListener((obs,o,n) -> {
            if (n != null && !Objects.equals(entry.fieldKey(), n.key())) { entry.chooseField(n.key()); refresh(); }
        });
        field.getStyleClass().add("dse-workspace-field-picker");

        Label confidence = new Label(entry.autoFieldKey().isBlank() ? "—" : Math.round(entry.confidence()*100) + "%");
        confidence.setMinWidth(54); confidence.setAlignment(Pos.CENTER);
        confidence.getStyleClass().add("dse-workspace-confidence");

        Label status = new Label(prettyState(entry.state()));
        status.setMinWidth(102); status.setAlignment(Pos.CENTER);
        status.getStyleClass().addAll("dse-workspace-status", statusClass(entry.state()));

        Button confirm = mini("Confirm", "complete", () -> { entry.confirm(); refresh(); });
        Button reset = mini("Reset Auto", "reset", () -> { entry.resetAuto(); refresh(); });
        Button staticBtn = mini("Keep Static", "lock", () -> { entry.keepStatic(); refresh(); });
        reset.setDisable(entry.autoFieldKey().isBlank());
        VBox actions = new VBox(6, confirm, reset, staticBtn);
        actions.setMinWidth(132); actions.setPrefWidth(132);

        Label behavior = new Label(entry.behaviorSummary());
        behavior.setWrapText(true); behavior.getStyleClass().add("dse-workspace-secondary");
        VBox fieldBox = new VBox(4, field, fieldKeyLabel(entry), behavior);
        HBox.setHgrow(fieldBox, Priority.ALWAYS);

        HBox row = new HBox(12, sourceBox, fieldBox, confidence, status, actions);
        row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("dse-workspace-mapping-row");
        updateRowDecor(entry, row);
        return row;
    }

    private Node financialRoleRow(Entry entry) {
        Label source = new Label(entry.sourceLabel());
        source.setMinWidth(250); source.setPrefWidth(260); source.setWrapText(true);
        source.getStyleClass().add("dse-workspace-source-label");
        Label label = new Label(entry.displayFieldLabel().isBlank() ? entry.fieldKey() : entry.displayFieldLabel());
        label.setWrapText(true); label.getStyleClass().add("dse-workspace-source-label");
        Label key = new Label(entry.fieldKey()); key.setWrapText(true); key.getStyleClass().add("dse-workspace-secondary");
        VBox field = new VBox(3, label, key); HBox.setHgrow(field, Priority.ALWAYS);
        Label confidence = new Label(Math.round(entry.confidence()*100) + "%");
        confidence.setMinWidth(54); confidence.setAlignment(Pos.CENTER); confidence.getStyleClass().add("dse-workspace-confidence");
        Label status = new Label("ERP Dynamic");
        status.setMinWidth(112); status.setAlignment(Pos.CENTER);
        status.getStyleClass().addAll("dse-workspace-status", "dse-workspace-status-confirmed");
        HBox row = new HBox(12, source, field, confidence, status);
        row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("dse-workspace-mapping-row");
        return row;
    }

    private Label autoSuggestion(Entry entry) {
        String text = entry.autoFieldKey().isBlank() ? "No reliable automatic suggestion" : "Auto: " + entry.autoFieldKey();
        Label label = new Label(text); label.setWrapText(true); label.getStyleClass().add("dse-workspace-secondary"); return label;
    }

    private Label fieldKeyLabel(Entry entry) {
        Label label = new Label(entry.fieldKey().isBlank() ? "Choose an ERP field" : entry.fieldKey());
        label.setWrapText(true); label.getStyleClass().add("dse-workspace-secondary"); return label;
    }

    private Node financialCard(Entry entry) {
        Label title = new Label("Dynamic Financial Summary"); title.getStyleClass().add("dse-workspace-card-title");
        Label note = new Label("ERP controls calculation values and row composition. Detected source roles are listed below so users can see Basic/Discount/Charges/GST/IGST/Round Off/Grand Total without creating duplicate calculation mappings.");
        note.setWrapText(true); note.getStyleClass().add("dse-workspace-section-help");

        TextField x = numeric(entry.x()), y = numeric(entry.y()), w = numeric(entry.width()), h = numeric(entry.height());
        TextField ratio = numeric(entry.summaryLabelRatio()*100.0);
        ComboBox<String> anchor = new ComboBox<>(FXCollections.observableArrayList("ABSOLUTE","TOP","BOTTOM","AFTER","BEFORE"));
        anchor.getSelectionModel().select(entry.anchorMode() == null ? "ABSOLUTE" : entry.anchorMode());
        ComboBox<String> growth = new ComboBox<>(FXCollections.observableArrayList("UP","DOWN","BOTH","FIXED"));
        growth.getSelectionModel().select(entry.growthDirection() == null ? "UP" : entry.growthDirection());

        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(10);
        addGrid(grid,0,"X",x,"Y",y); addGrid(grid,1,"Width",w,"Height",h);
        addGrid(grid,2,"Label area %",ratio,"Anchor",anchor); addGrid(grid,3,"Growth",growth,"",new Label(""));
        ColumnConstraints l1=new ColumnConstraints(110), f1=new ColumnConstraints(170), l2=new ColumnConstraints(110), f2=new ColumnConstraints(190);
        f1.setHgrow(Priority.ALWAYS); f2.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().addAll(l1,f1,l2,f2);

        Button apply = action("Apply Financial Layout", "apply", () -> {
            entry.setFinancialGeometry(parse(x,entry.x()), parse(y,entry.y()), Math.max(20,parse(w,entry.width())), Math.max(20,parse(h,entry.height())),
                    Math.max(.35,Math.min(.85,parse(ratio,entry.summaryLabelRatio()*100.0)/100.0)), anchor.getValue(), growth.getValue());
            refresh();
        });
        Button reset = action("Reset Auto", "reset", () -> { entry.resetAuto(); refresh(); });
        HBox buttons = new HBox(9, apply, reset); buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox card = new VBox(10, title, note, grid, buttons); card.getStyleClass().add("dse-workspace-mapping-row");
        return card;
    }

    private void updateRowDecor(Entry entry, Node node) {
        if (node == null) return;
        node.getStyleClass().removeIf(v -> v.startsWith("dse-workspace-row-state-"));
        node.getStyleClass().add("dse-workspace-row-state-" + entry.state().toLowerCase(Locale.ROOT).replace('_','-'));
    }

    private String sectionCount(NavigationItem item) {
        int total = session.count(item);
        if (total == 0) return "—";
        long review = session.entries(item).stream().filter(e -> !e.readOnly())
                .filter(e -> "REVIEW_REQUIRED".equals(e.state()) || "UNMAPPED".equals(e.state())).count();
        long complete = Math.max(0, total - review);
        return review == 0 ? total + "/" + total + " ✓" : complete + "/" + total + " !";
    }

    private NavigationItem firstUsefulSection() {
        return session.navigationItems().stream().filter(i -> session.count(i) > 0).findFirst()
                .orElseGet(() -> session.navigationItems().stream().findFirst()
                        .orElse(new NavigationItem(Section.HEADER.name(), Section.HEADER.label(), Section.HEADER.icon(), Section.HEADER, "", false)));
    }

    private NavigationItem initialNavigation(Section initialSection) {
        if (initialSection == null) return firstUsefulSection();
        return session.navigationItems().stream().filter(i -> i.section() == initialSection).findFirst().orElse(firstUsefulSection());
    }

    private String helpFor(Section section) {
        return switch (section) {
            case HEADER -> "Review invoice/document header values. Fixed labels remain source artwork; only changing ERP values are mapped.";
            case BILLING -> "Customer name, Billing Address and GSTIN belong together. Address is one multiline ERP field; detected phone/email/contact fields join the same block automatically.";
            case DELIVERY -> "Delivery/Ship To is independent from Billing, so long addresses and any detected delivery contact values can flow without changing the saved template.";
            case TRANSPORT -> "Review Transporter, GSTIN, Vehicle, Contact and any additional supported transport values. Label + value source text exposes the changing value only.";
            case DETECTED_BLOCKS -> "Generic source blocks are discovered from PDF geometry/context. Mixed supported values such as phone, email, contact, dates and references use this same review flow without a new controller.";
            case ITEMS -> "Physical PDF headers keep their X/width. Change only the ERP meaning; confirmed/manual choices survive re-detection.";
            case FINANCIAL -> "Do not map tax/charge rows individually. ERP owns dynamic rows; the popup shows detected Basic/Discount/Charges/GST/IGST/Round Off/Grand Total roles and lets you review only source-block layout.";
            case PAYMENT -> "Review Bank/Payment fields. Static captions remain untouched unless you intentionally map them.";
            case TERMS_FOOTER -> "Payment Terms and Terms & Conditions are separate mappings. Terms/Notes are multiline blocks; Signature may replace raster or vector source artwork.";
        };
    }

    private String groupHelp(Section section) {
        return switch (section) {
            case FINANCIAL -> "Detected financial source block • calculation values remain ERP controlled";
            case ITEMS -> "Detected physical item table • header geometry stays protected";
            case DETECTED_BLOCKS -> "Detected source block • values below use the same centralized mapping flow";
            default -> "Detected source block • values below use the same centralized mapping flow";
        };
    }

    private static Button action(String text, String semantic, Runnable action) {
        Button b = new Button(text); b.getStyleClass().add("dse-workspace-action"); SemanticIconManager.apply(b, semantic); b.setOnAction(e -> action.run()); return b;
    }
    private static Button mini(String text, String semantic, Runnable action) {
        Button b = action(text, semantic, action); b.getStyleClass().add("dse-workspace-action-mini"); return b;
    }
    private static String prettyState(String state) {
        if (state == null || state.isBlank()) return "Unmapped";
        return switch (state) { case "MANUAL_OVERRIDE" -> "Manual"; case "REVIEW_REQUIRED" -> "Review"; default -> state.substring(0,1)+state.substring(1).toLowerCase(Locale.ROOT); };
    }
    private static String statusClass(String state) { return "dse-workspace-status-" + (state == null ? "unmapped" : state.toLowerCase(Locale.ROOT).replace('_','-')); }
    private static void selectField(ComboBox<TemplateFieldDefinition> combo, String key) {
        if (key == null || key.isBlank()) return;
        combo.getItems().stream().filter(f -> key.equals(f.key())).findFirst().ifPresent(combo.getSelectionModel()::select);
    }
    private static TextField numeric(double value) { TextField f = new TextField(String.format(Locale.ROOT,"%.1f",value)); f.setPrefColumnCount(8); return f; }
    private static double parse(TextField field, double fallback) { try { return Double.parseDouble(field.getText().trim()); } catch (Exception ignored) { return fallback; } }
    private static void addGrid(GridPane grid, int row, String l1, Node f1, String l2, Node f2) {
        Label a=new Label(l1), b=new Label(l2); a.getStyleClass().add("dse-workspace-field-label"); b.getStyleClass().add("dse-workspace-field-label");
        grid.add(a,0,row); grid.add(f1,1,row); grid.add(b,2,row); grid.add(f2,3,row);
        if (f1 instanceof Region r) r.setMaxWidth(Double.MAX_VALUE); if (f2 instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
    }
}
