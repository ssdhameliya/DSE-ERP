package org.example.service;

import org.example.util.BusinessClock;
import org.example.util.IconFactory;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.PopupTableWorkspace;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.example.api.returns.ReturnApiClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

/**
 * One-screen editor for sales and purchase returns. It supports multiple
 * invoice lines, independently entered quantities and one reason per item.
 */
public final class ReturnEditorService {
    public enum Type {
        SALES("SALES RETURN", "SAL-RET-", 1),
        PURCHASE("PURCHASE RETURN", "PUR-RET-", -1);

        private final String databaseValue;
        private final String numberPrefix;
        private final int stockDirection;

        Type(String databaseValue, String numberPrefix, int stockDirection) {
            this.databaseValue = databaseValue;
            this.numberPrefix = numberPrefix;
            this.stockDirection = stockDirection;
        }
    }

    /** Immutable invoice-line input used by both sales and purchase screens. */
    public record InvoiceItem(String code, String description, double quantity,
                              double rate, double taxPercent) {
    }

    private ReturnEditorService() {
    }

    /** Opens the editor and returns the created return number when saved. */
    public static Optional<String> show(Window owner, Type type, String invoiceNo,
                                        String partyName, int partyId,
                                        List<InvoiceItem> invoiceItems) {
        if (invoiceItems == null || invoiceItems.isEmpty()) {
            warning("This invoice does not contain any returnable items.");
            return Optional.empty();
        }

        List<ReturnLine> rows = loadReturnableLines(type, invoiceNo, invoiceItems);
        if (rows.stream().noneMatch(row -> row.available() > 0.0001)) {
            warning("Every item on " + invoiceNo + " has already been returned.");
            return Optional.empty();
        }

        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(type == Type.SALES ? "Create Sales Return" : "Create Purchase Return");
        dialog.setHeaderText("Select invoice items and enter the quantity to return.");

        DatePicker returnDate = new DatePicker(BusinessClock.today());
        returnDate.setMaxWidth(Double.MAX_VALUE);
        Label total = PopupTableWorkspace.metricValue(money(0), "warning");

        TableView<ReturnLine> table = buildTable();
        table.getItems().setAll(rows);
        table.setPrefSize(1080, 430);
        table.setEditable(true);
        PopupTableWorkspace.prepareTable(table, "erp-table-profile-dialog");

        Runnable updateTotal = () -> total.setText(money(rows.stream()
            .filter(ReturnLine::selected)
            .mapToDouble(ReturnLine::returnAmount)
            .sum()));
        rows.forEach(row -> {
            row.selectedProperty().addListener((observable, oldValue, newValue) -> {
                // Checking a line means "return the remaining quantity" by
                // default. The visible quantity editor can then be reduced
                // for a partial return.
                if (newValue && row.returnQuantity() <= 0 && row.available() > 0) {
                    row.setReturnQuantity(row.available());
                } else if (!newValue) {
                    row.setReturnQuantity(0);
                }
                table.refresh();
                updateTotal.run();
            });
            row.returnQuantityProperty().addListener((observable, oldValue, newValue) -> updateTotal.run());
        });

        CheckBox selectAvailable = new CheckBox("Select all available items");
        selectAvailable.getProperties().put("erp.icon.skip", true);
        selectAvailable.setOnAction(event -> {
            rows.forEach(row -> row.setSelected(selectAvailable.isSelected() && row.available() > 0.0001));
            updateTotal.run();
        });

        Label selectedCount = PopupTableWorkspace.footerText("0 items selected");
        Runnable updateSelectedCount = () -> {
            long count = rows.stream().filter(ReturnLine::selected).count();
            selectedCount.setText(count + " item" + (count == 1 ? "" : "s") + " selected");
        };
        rows.forEach(row -> row.selectedProperty().addListener((o, a, b) -> updateSelectedCount.run()));
        updateSelectedCount.run();

        Label invoiceValue = PopupTableWorkspace.metricValue(invoiceNo, "document");
        Label partyValue = PopupTableWorkspace.metricValue(partyName, type == Type.SALES ? "customer" : "supplier");
        HBox metrics = PopupTableWorkspace.metricStrip(
            PopupTableWorkspace.metricCard("Invoice", invoiceValue, "document"),
            PopupTableWorkspace.metricCard(type == Type.SALES ? "Customer" : "Supplier", partyValue, type == Type.SALES ? "customer" : "supplier"),
            PopupTableWorkspace.controlCard("Return Date", returnDate, "calendar"),
            PopupTableWorkspace.metricCard("Estimated Total", total, "warning")
        );
        HBox selectionBar = PopupTableWorkspace.footer(selectAvailable);
        VBox tableArea = new VBox(9, selectionBar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox footer = PopupTableWorkspace.footerWithRight(selectedCount,
            PopupTableWorkspace.footerText("Enter a reason for every selected return item."));
        VBox content = PopupTableWorkspace.content(metrics, tableArea, footer);
        dialog.getDialogPane().setContent(content);
        PopupTableWorkspace.prepareDialog(dialog, 1160);

        ButtonType save = new ButtonType("Create Return", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        while (true) {
            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() != save) return Optional.empty();
            try {
                List<ReturnLine> selected = validate(rows, returnDate.getValue());
                String returnNo = save(type, invoiceNo, partyId, returnDate.getValue(), selected);
                NotificationService.add((type == Type.SALES ? "Sales" : "Purchase") +
                    " return " + returnNo + " created with " + selected.size() + " item(s).");
                return Optional.of(returnNo);
            } catch (Exception exception) {
                warning(exception.getMessage() == null ? "The return could not be created." : exception.getMessage());
            }
        }
    }

    private static TableView<ReturnLine> buildTable() {
        TableView<ReturnLine> table = new TableView<>();
        table.getStyleClass().addAll("approved-table", "erp-table-profile-dialog");
        // Deliberate exception to numbered register rows: a return transaction
        // can select several invoice lines with a different quantity per line.
        table.getProperties().put("erp-keep-selection", true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ReturnLine, Boolean> selected = new TableColumn<>("Select");
        selected.setCellValueFactory(value -> value.getValue().selectedProperty());
        selected.setCellFactory(CheckBoxTableCell.forTableColumn(selected));
        selected.setEditable(true);
        selected.setMinWidth(62);
        selected.setMaxWidth(72);

        TableColumn<ReturnLine, String> code = textColumn("Item Code", 110,
            row -> row.codeProperty());
        TableColumn<ReturnLine, String> item = textColumn("Item Name", 210,
            row -> row.descriptionProperty());
        TableColumn<ReturnLine, Number> invoiced = numberColumn("Invoiced", 88,
            row -> row.invoicedProperty());
        TableColumn<ReturnLine, Number> returned = numberColumn("Returned", 88,
            row -> row.returnedProperty());
        TableColumn<ReturnLine, Number> available = numberColumn("Available", 88,
            row -> row.availableProperty());

        TableColumn<ReturnLine, ReturnLine> quantity = new TableColumn<>("Return Qty");
        quantity.setCellValueFactory(value -> new javafx.beans.property.ReadOnlyObjectWrapper<>(value.getValue()));
        quantity.setCellFactory(column -> new QuantityEditorCell());
        quantity.setPrefWidth(95);

        TableColumn<ReturnLine, Number> rate = numberColumn("Rate", 90, row -> row.rateProperty());
        TableColumn<ReturnLine, Number> tax = numberColumn("Tax %", 72, row -> row.taxProperty());
        TableColumn<ReturnLine, Number> amount = numberColumn("Return Amount", 120,
            row -> new SimpleDoubleProperty(row.returnAmount()));
        amount.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty) {
                    textProperty().unbind();
                    setText(null);
                } else {
                    ReturnLine row = getTableRow().getItem();
                    if (row != null) {
                        textProperty().unbind();
                        textProperty().bind(row.returnQuantityProperty().multiply(
                            row.rate() * (1 + row.tax() / 100.0)).asString("₹ %,.2f"));
                    }
                }
            }
        });

        TableColumn<ReturnLine, ReturnLine> reason = new TableColumn<>("Reason");
        reason.setCellValueFactory(value -> new javafx.beans.property.ReadOnlyObjectWrapper<>(value.getValue()));
        reason.setCellFactory(column -> new ReasonEditorCell());
        reason.setPrefWidth(190);

        IconFactory.applyTableHeaderIcon(selected, "select");
        IconFactory.applyTableHeaderIcon(code, "identity");
        IconFactory.applyTableHeaderIcon(item, "item");
        IconFactory.applyTableHeaderIcon(invoiced, "document");
        IconFactory.applyTableHeaderIcon(returned, "return");
        IconFactory.applyTableHeaderIcon(available, "quantity");
        IconFactory.applyTableHeaderIcon(quantity, "return");
        IconFactory.applyTableHeaderIcon(rate, "currency");
        IconFactory.applyTableHeaderIcon(tax, "tax");
        IconFactory.applyTableHeaderIcon(amount, "currency");
        IconFactory.applyTableHeaderIcon(reason, "notes");

        table.getColumns().addAll(selected, code, item, invoiced, returned, available,
            quantity, rate, tax, amount, reason);
        return table;
    }

    private static TableColumn<ReturnLine, String> textColumn(
        String title, double width,
        java.util.function.Function<ReturnLine, javafx.beans.value.ObservableValue<String>> value) {
        TableColumn<ReturnLine, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> value.apply(cell.getValue()));
        column.setPrefWidth(width);
        return column;
    }

    private static TableColumn<ReturnLine, Number> numberColumn(
        String title, double width,
        java.util.function.Function<ReturnLine, javafx.beans.value.ObservableValue<Number>> value) {
        TableColumn<ReturnLine, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> value.apply(cell.getValue()));
        column.setPrefWidth(width);
        return column;
    }

    /**
     * Always-visible quantity editor. Unlike TextFieldTableCell, this does not
     * require a hidden double-click/Enter sequence and is therefore usable as
     * soon as the return dialog opens.
     */
    private static final class QuantityEditorCell extends TableCell<ReturnLine, ReturnLine> {
        private final TextField editor = new TextField();
        private boolean refreshing;

        QuantityEditorCell() {
            editor.setPromptText("0");
            editor.setMaxWidth(Double.MAX_VALUE);
            editor.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d*(\\.\\d{0,3})?") ? change : null));
            editor.textProperty().addListener((observable, oldValue, newValue) -> {
                if (refreshing || getItem() == null) return;
                double value = parseQuantity(newValue);
                getItem().setReturnQuantity(value);
                if (value > 0 && !getItem().selected()) getItem().setSelected(true);
            });
        }

        @Override protected void updateItem(ReturnLine row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            refreshing = true;
            editor.setText(row.returnQuantity() <= 0 ? "" : formatQuantity(row.returnQuantity()));
            editor.setDisable(row.available() <= 0.0001);
            refreshing = false;
            setGraphic(editor);
        }
    }

    /** Always-visible reason editor shared by sales and purchase returns. */
    private static final class ReasonEditorCell extends TableCell<ReturnLine, ReturnLine> {
        private final TextField editor = new TextField();
        private boolean refreshing;

        ReasonEditorCell() {
            editor.setPromptText("Reason for return");
            editor.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!refreshing && getItem() != null) getItem().setReason(newValue);
            });
        }

        @Override protected void updateItem(ReturnLine row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            refreshing = true;
            editor.setText(row.reason());
            refreshing = false;
            setGraphic(editor);
        }
    }

    private static double parseQuantity(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static List<ReturnLine> loadReturnableLines(Type type, String invoiceNo, List<InvoiceItem> items) {
        try {
            Map<String,Double> returned = new ReturnApiClient().returned(type.databaseValue, invoiceNo);
            List<ReturnLine> rows = new ArrayList<>();
            for (InvoiceItem item : items) rows.add(new ReturnLine(item, returned.getOrDefault(item.code(),0.0)));
            return rows;
        } catch (Exception exception) { throw new IllegalStateException("Previously returned quantities could not be loaded", exception); }
    }

    private static List<ReturnLine> validate(List<ReturnLine> rows, LocalDate returnDate) {
        if (returnDate == null) throw new IllegalArgumentException("Select the return date.");
        List<ReturnLine> selected = rows.stream().filter(ReturnLine::selected).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("Select at least one item to return.");
        for (ReturnLine row : selected) {
            if (row.returnQuantity() <= 0) {
                throw new IllegalArgumentException("Enter a return quantity for " + row.description());
            }
            if (row.returnQuantity() > row.available() + 0.0001) {
                throw new IllegalArgumentException(row.description() + " has only " +
                    formatQuantity(row.available()) + " available to return.");
            }
            if (row.reason().isBlank()) {
                throw new IllegalArgumentException("Enter a return reason for " + row.description());
            }
        }
        return selected;
    }

    private static String save(Type type, String invoiceNo, int partyId, LocalDate returnDate, List<ReturnLine> rows) {
        List<ReturnApiClient.CreateLine> lines=rows.stream().map(r->new ReturnApiClient.CreateLine(r.code(),r.returnQuantity(),r.returnAmount(),r.reason())).toList();
        return new ReturnApiClient().create(new ReturnApiClient.CreateRequest(type.databaseValue,invoiceNo,partyId,returnDate.toString(),lines));
    }

    private static String money(double value) {
        return String.format(Locale.getDefault(), "₹ %,.2f", value);
    }

    private static String formatQuantity(double value) {
        return String.format(Locale.getDefault(), "%,.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static void warning(String message) {
        new OwnedAlert(Alert.AlertType.WARNING, message).showAndWait();
    }

    /** Mutable row model used by the editable return table. */
    private static final class ReturnLine {
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final StringProperty code;
        private final StringProperty description;
        private final DoubleProperty invoiced;
        private final DoubleProperty returned;
        private final DoubleProperty available;
        private final DoubleProperty returnQuantity = new SimpleDoubleProperty(0);
        private final DoubleProperty rate;
        private final DoubleProperty tax;
        private final StringProperty reason = new SimpleStringProperty("Return requested");

        ReturnLine(InvoiceItem item, double alreadyReturned) {
            code = new SimpleStringProperty(item.code());
            description = new SimpleStringProperty(item.description());
            invoiced = new SimpleDoubleProperty(item.quantity());
            returned = new SimpleDoubleProperty(alreadyReturned);
            available = new SimpleDoubleProperty(Math.max(0, item.quantity() - alreadyReturned));
            rate = new SimpleDoubleProperty(item.rate());
            tax = new SimpleDoubleProperty(item.taxPercent());
        }

        boolean selected() { return selected.get(); }
        void setSelected(boolean value) { selected.set(value); }
        BooleanProperty selectedProperty() { return selected; }
        String code() { return code.get(); }
        StringProperty codeProperty() { return code; }
        String description() { return description.get(); }
        StringProperty descriptionProperty() { return description; }
        DoubleProperty invoicedProperty() { return invoiced; }
        DoubleProperty returnedProperty() { return returned; }
        double available() { return available.get(); }
        DoubleProperty availableProperty() { return available; }
        double returnQuantity() { return returnQuantity.get(); }
        void setReturnQuantity(double value) { returnQuantity.set(value); }
        DoubleProperty returnQuantityProperty() { return returnQuantity; }
        double rate() { return rate.get(); }
        DoubleProperty rateProperty() { return rate; }
        double tax() { return tax.get(); }
        DoubleProperty taxProperty() { return tax; }
        String reason() { return reason.get() == null ? "" : reason.get().trim(); }
        void setReason(String value) { reason.set(value == null ? "" : value); }
        StringProperty reasonProperty() { return reason; }
        double returnAmount() { return returnQuantity() * rate() * (1 + tax() / 100.0); }
    }
}
