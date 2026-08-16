package org.example.util;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.api.support.SupportApiClient;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Shared, read-only payment drill-down for Sales and Purchase registers. */
public final class InvoicePaymentDetailsDialog {
    private InvoicePaymentDetailsDialog() {}

    public static void show(Node owner, SupportApiClient api, String documentType, int documentId,
                            String invoiceNo, String partyLabel, String partyName,
                            double total, double paid, double balance) {
        List<SupportApiClient.PaymentRow> payments = api.payments(documentType, documentId);
        OwnedDialog<ButtonType> dialog = new OwnedDialog<>(owner);
        dialog.setTitle("Invoice Payment Details");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getStyleClass().addAll("invoice-payment-dialog", "modern-dialog");
        dialog.getDialogPane().setPrefSize(920, 590);

        Label title = new Label("Invoice Payment Details");
        title.getStyleClass().add("bank-dialog-hero-title");
        Label subtitle = new Label(invoiceNo + "  •  " + partyLabel + ": " + safe(partyName));
        subtitle.getStyleClass().add("bank-dialog-subtitle");
        HBox hero = new HBox(12, IconFactory.icon("payment", 32), new VBox(3, title, subtitle));
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("bank-dialog-hero");

        GridPane metrics = new GridPane();
        metrics.setHgap(12);
        metrics.add(metric("Invoice Total", money(total), "payment-metric-total"), 0, 0);
        metrics.add(metric("Paid", money(paid), "payment-metric-paid"), 1, 0);
        metrics.add(metric("Outstanding", money(balance), balance > .009 ? "payment-metric-pending" : "payment-metric-settled"), 2, 0);
        metrics.add(metric("Payment Status", balance <= .009 ? "PAID" : paid > .009 ? "PARTIAL" : "PENDING",
                balance <= .009 ? "payment-metric-settled" : "payment-metric-pending"), 3, 0);
        for (Node child : metrics.getChildren()) {
            GridPane.setHgrow(child, Priority.ALWAYS);
            if (child instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        }

        TableView<SupportApiClient.PaymentRow> table = new TableView<>();
        table.getStyleClass().addAll("professional-table", "approved-table", "invoice-payment-table", "erp-table-profile-dialog");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No payment records have been recorded for this invoice."));
        TableColumn<SupportApiClient.PaymentRow, String> date = textColumn("Date", "calendar", SupportApiClient.PaymentRow::date);
        TableColumn<SupportApiClient.PaymentRow, String> reference = textColumn("Reference", "document", SupportApiClient.PaymentRow::reference);
        TableColumn<SupportApiClient.PaymentRow, String> from = textColumn("Received From / Paid To", "customer", SupportApiClient.PaymentRow::receivedFrom);
        TableColumn<SupportApiClient.PaymentRow, String> mode = textColumn("Mode", "payment", SupportApiClient.PaymentRow::mode);
        TableColumn<SupportApiClient.PaymentRow, Number> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().amount()));
        amount.setCellFactory(ignored -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : money(value.doubleValue()));
                setAlignment(Pos.CENTER_RIGHT);
                getStyleClass().removeAll("register-total-amount");
                if (!empty) getStyleClass().add("register-total-amount");
            }
        });
        IconFactory.applyTableHeaderIcon(amount, "currency");
        TableColumn<SupportApiClient.PaymentRow, String> notes = textColumn("Notes", "notes", SupportApiClient.PaymentRow::notes);
        table.getColumns().addAll(date, reference, from, mode, amount, notes);
        table.getItems().setAll(payments);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label count = new Label(payments.size() + (payments.size() == 1 ? " payment record" : " payment records"));
        count.getStyleClass().add("muted-label");
        Label totalRecorded = new Label("Recorded total: " + money(payments.stream().mapToDouble(SupportApiClient.PaymentRow::amount).sum()));
        totalRecorded.getStyleClass().add("payment-history-total");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(10, count, spacer, totalRecorded);
        footer.setAlignment(Pos.CENTER_LEFT);

        dialog.getDialogPane().setContent(new VBox(12, hero, metrics, table, footer));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static VBox metric(String label, String value, String style) {
        Label caption = new Label(label);
        caption.getStyleClass().add("bank-dialog-label");
        Label amount = new Label(value);
        amount.getStyleClass().addAll("bank-dialog-metric-value", style);
        VBox box = new VBox(4, caption, amount);
        box.getStyleClass().add("bank-dialog-metric-card");
        return box;
    }

    private static TableColumn<SupportApiClient.PaymentRow, String> textColumn(
            String title, String semantic, java.util.function.Function<SupportApiClient.PaymentRow, String> getter) {
        TableColumn<SupportApiClient.PaymentRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(safe(getter.apply(value.getValue()))));
        IconFactory.applyTableHeaderIcon(column, semantic);
        return column;
    }

    private static String money(double value) {
        return NumberFormat.getCurrencyInstance(Locale.of("en", "IN")).format(value).replace("₹", "₹ ");
    }

    private static String safe(String value) { return value == null || value.isBlank() ? "—" : value; }
}
