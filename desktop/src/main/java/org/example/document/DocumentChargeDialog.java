package org.example.document;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.model.PurchaseCharge;
import org.example.model.SalesCharge;
import org.example.util.IconFactory;
import org.example.util.OwnedDialog;

import java.util.*;
import java.util.function.Consumer;

/**
 * Shared additional-charge editor used by Sales and Purchase documents.
 *
 * <p>The original controllers contained two nearly identical modal editors.
 * Centralising the editor keeps validation, tax treatment and presentation
 * consistent without creating an inheritance relationship between Sales and
 * Purchase controllers.</p>
 */
public final class DocumentChargeDialog {
    private DocumentChargeDialog() { }

    public static Optional<List<SalesCharge>> editSales(List<SalesCharge> current,
                                                        List<String> availableTypes,
                                                        Consumer<String> warning) {
        return edit(current, availableTypes, warning,
            "Additional Charges", "Add additional invoice charges as required",
            "Add as many charges as required", 175, 680, 440, 740, 480,
            SALES_ADAPTER);
    }

    public static Optional<List<PurchaseCharge>> editPurchase(List<PurchaseCharge> current,
                                                              List<String> availableTypes,
                                                              Consumer<String> warning) {
        return edit(current, availableTypes, warning,
            "Purchase Additional Charges", "Add purchase charges with the same GST / IGST calculation rules as the invoice",
            "Add as many purchase charges as required", 230, 700, 450, 720, 450,
            PURCHASE_ADAPTER);
    }

    public static String validateSales(List<SalesCharge> charges) {
        return validate(charges, SALES_ADAPTER);
    }

    public static String validatePurchase(List<PurchaseCharge> charges) {
        return validate(charges, PURCHASE_ADAPTER);
    }

    private static <T> Optional<List<T>> edit(List<T> current, List<String> availableTypes, Consumer<String> warning,
                                               String title, String header, String limitText,
                                               double viewportHeight, double minWidth, double minHeight,
                                               double prefWidth, double prefHeight, Adapter<T> adapter) {
        List<T> draft = new ArrayList<>();
        if (current != null) current.forEach(value -> draft.add(adapter.copy(value)));
        List<String> safeTypes = availableTypes == null ? List.of() : availableTypes;

        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getStyleClass().add("sales-charge-dialog");

        VBox rows = new VBox(9);
        rows.getStyleClass().add("sales-charge-editor-rows");
        Label totals = new Label();
        totals.getStyleClass().add("sales-charge-editor-total");
        Button add = new Button("Add Charge", IconFactory.compactIcon("add", 14));
        add.getStyleClass().addAll("approved-button", "approved-primary-button", "sales-charge-add");
        Label limit = new Label(limitText);
        limit.getStyleClass().add("sales-charge-limit");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox addBar = new HBox(10, add, spacer, limit);
        addBar.setAlignment(Pos.CENTER_LEFT);

        Runnable updateTotals = () -> {
            double amount = draft.stream().mapToDouble(adapter::amount).sum();
            double tax = draft.stream().mapToDouble(adapter::taxAmount).sum();
            totals.setText(String.format("Charges ₹ %,.2f    GST ₹ %,.2f    Total ₹ %,.2f", amount, tax, amount + tax));
            add.setDisable(false);
        };

        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            rows.getChildren().clear();
            for (int index = 0; index < draft.size(); index++) {
                T charge = draft.get(index);
                ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(safeTypes));
                String selectedType = adapter.type(charge);
                if (!selectedType.isBlank() && !type.getItems().contains(selectedType)) type.getItems().add(selectedType);
                type.setValue(selectedType.isBlank() ? null : selectedType);
                type.setPromptText("Select charge...");
                type.setMaxWidth(Double.MAX_VALUE);

                TextField amount = new TextField(adapter.amount(charge) <= 0 ? "" : String.format(Locale.ROOT, "%.2f", adapter.amount(charge)));
                amount.setPromptText("Amount");
                ComboBox<String> tax = new ComboBox<>(FXCollections.observableArrayList(
                    "Non-taxable", "Taxable 0%", "Taxable 5%", "Taxable 12%", "Taxable 18%", "Taxable 28%"));
                tax.setValue(adapter.taxable(charge) ? "Taxable " + percentText(adapter.gst(charge)) : "Non-taxable");
                Button remove = new Button("Remove", IconFactory.compactIcon("delete", 13));
                remove.getStyleClass().addAll("approved-button", "approved-danger-button", "sales-charge-remove");

                int rowIndex = index;
                type.valueProperty().addListener((o, a, b) -> adapter.type(charge, b));
                amount.textProperty().addListener((o, a, b) -> { adapter.amount(charge, parseAmount(b)); updateTotals.run(); });
                tax.valueProperty().addListener((o, a, b) -> { applyTaxTreatment(charge, b, adapter); updateTotals.run(); });
                remove.setOnAction(e -> { draft.remove(rowIndex); render[0].run(); });

                GridPane row = new GridPane();
                row.setHgap(8); row.setVgap(3);
                row.getStyleClass().add("sales-charge-editor-row");
                row.add(new Label("Charge " + (index + 1)), 0, 0);
                row.add(new Label("Amount"), 1, 0);
                row.add(new Label("Tax Treatment"), 2, 0);
                row.add(type, 0, 1); row.add(amount, 1, 1); row.add(tax, 2, 1); row.add(remove, 3, 1);
                GridPane.setHgrow(type, Priority.ALWAYS);
                rows.getChildren().add(row);
            }
            if (draft.isEmpty()) {
                Label empty = new Label("No additional charges. Select Add Charge when required.");
                empty.getStyleClass().add("sales-charge-editor-empty");
                rows.getChildren().add(empty);
            }
            updateTotals.run();
        };
        add.setOnAction(e -> { draft.add(adapter.create()); render[0].run(); });
        render[0].run();

        ScrollPane scroller = new ScrollPane(rows);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setPannable(true);
        scroller.setPrefViewportHeight(viewportHeight);
        if (viewportHeight <= 180) { scroller.setMinHeight(120); scroller.setMaxHeight(210); }
        scroller.getStyleClass().add("sales-charge-editor-scroll");

        VBox content = new VBox(12, scroller, addBar, new Separator(), totals);
        content.setPrefWidth(prefWidth);
        if (viewportHeight <= 180) content.setMinHeight(260);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinSize(minWidth, minHeight);
        if (prefWidth > 0 && prefHeight > 0) dialog.getDialogPane().setPrefSize(prefWidth, prefHeight);
        dialog.setResizable(true);

        ButtonType apply = new ButtonType("Apply Charges", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, apply);
        Node applyButton = dialog.getDialogPane().lookupButton(apply);
        applyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validate(draft, adapter);
            if (error != null) {
                event.consume();
                if (warning != null) warning.accept(error);
            }
        });
        return dialog.showAndWait().filter(apply::equals)
            .map(ignored -> draft.stream().map(adapter::copy).toList());
    }

    private static <T> String validate(List<T> charges, Adapter<T> adapter) {
        if (charges == null || charges.isEmpty()) return null;
        Set<String> names = new HashSet<>();
        for (T charge : charges) {
            if (charge == null || adapter.type(charge).isBlank()) return "Select a charge type for every charge row.";
            if (adapter.amount(charge) <= 0) return "Charge amount must be greater than zero.";
            if (!names.add(normalized(adapter.type(charge)))) return "The same charge type cannot be selected twice.";
        }
        return null;
    }

    private static <T> void applyTaxTreatment(T charge, String treatment, Adapter<T> adapter) {
        if (treatment == null || treatment.startsWith("Non")) {
            adapter.taxable(charge, false);
            adapter.gst(charge, 0);
            return;
        }
        adapter.taxable(charge, true);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9.]+)").matcher(treatment);
        adapter.gst(charge, matcher.find() ? Double.parseDouble(matcher.group(1)) : 0);
    }

    private static double parseAmount(String value) {
        try { return value == null || value.isBlank() ? 0 : Double.parseDouble(value.replace(",", "").trim()); }
        catch (Exception ignored) { return 0; }
    }

    private static String percentText(double value) {
        return Math.rint(value) == value
            ? String.format(Locale.ROOT, "%.0f%%", value)
            : String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private interface Adapter<T> {
        T create(); T copy(T value);
        String type(T value); void type(T value, String type);
        double amount(T value); void amount(T value, double amount);
        boolean taxable(T value); void taxable(T value, boolean taxable);
        double gst(T value); void gst(T value, double gst);
        double taxAmount(T value);
    }

    private static final Adapter<SalesCharge> SALES_ADAPTER = new Adapter<>() {
        public SalesCharge create() { return new SalesCharge("", 0, true, 18); }
        public SalesCharge copy(SalesCharge value) { return value.copy(); }
        public String type(SalesCharge value) { return value.getChargeType(); }
        public void type(SalesCharge value, String type) { value.setChargeType(type); }
        public double amount(SalesCharge value) { return value.getAmount(); }
        public void amount(SalesCharge value, double amount) { value.setAmount(amount); }
        public boolean taxable(SalesCharge value) { return value.isTaxable(); }
        public void taxable(SalesCharge value, boolean taxable) { value.setTaxable(taxable); }
        public double gst(SalesCharge value) { return value.getGstPercent(); }
        public void gst(SalesCharge value, double gst) { value.setGstPercent(gst); }
        public double taxAmount(SalesCharge value) { return value.getTaxAmount(); }
    };

    private static final Adapter<PurchaseCharge> PURCHASE_ADAPTER = new Adapter<>() {
        public PurchaseCharge create() { return new PurchaseCharge("", 0, true, 18); }
        public PurchaseCharge copy(PurchaseCharge value) { return value.copy(); }
        public String type(PurchaseCharge value) { return value.getChargeType(); }
        public void type(PurchaseCharge value, String type) { value.setChargeType(type); }
        public double amount(PurchaseCharge value) { return value.getAmount(); }
        public void amount(PurchaseCharge value, double amount) { value.setAmount(amount); }
        public boolean taxable(PurchaseCharge value) { return value.isTaxable(); }
        public void taxable(PurchaseCharge value, boolean taxable) { value.setTaxable(taxable); }
        public double gst(PurchaseCharge value) { return value.getGstPercent(); }
        public void gst(PurchaseCharge value, double gst) { value.setGstPercent(gst); }
        public double taxAmount(PurchaseCharge value) { return value.getTaxAmount(); }
    };
}
