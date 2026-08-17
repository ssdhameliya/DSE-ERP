package org.example.controller;

import org.example.util.BusinessClock;

import org.example.util.OwnedAlert;
import org.example.util.OwnedDialog;
import org.example.util.OwnedTextInputDialog;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.StringConverter;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.example.model.*;
import org.example.config.ConfigManager;
import org.example.api.master.MasterApiClient;
import org.example.service.LookupService;

import org.example.navigation.NavigationManager;

import org.example.service.ItemService;
import org.example.service.NotificationService;
import org.example.service.PartyService;
import org.example.service.SalesService;
import org.example.util.IconFactory;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;
import org.example.util.UiTaskExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class SalesController {
    @FXML private Button btnAddCustomer;

    @FXML
    private VBox salesEntryRoot;

    @FXML
    private TextField txtInvoiceNo;

    @FXML
    private TextField txtQuantity;

    @FXML
    private TextField txtRate;

    @FXML
    private TextField txtGST;

    @FXML
    private TextField txtLineDiscount;

    @FXML
    private DatePicker dpInvoiceDate;
    @FXML private TextField txtPoDate;
    @FXML private ComboBox<String> cmbSalesPerson,cmbPaymentTerms;
    @FXML private ComboBox<String> cmbGstType,cmbChargeType;
    @FXML private ComboBox<Lookup> cmbTransporter;
    @FXML private TextField txtOtherCharges,txtTransport,txtReference,txtAttachment;
    @FXML private TextField txtVehicleNumber,txtContactPerson,txtContactPersonMobile,txtTransportNote,txtOrderNo;
    @FXML private TextField txtBillingGstin,txtDeliveryGstin,txtTransporterGstin,txtChargeAmount;
    @FXML private CheckBox chkSameAsBilling;
    @FXML private TextArea txtInvoiceMessage;

    @FXML
    private ComboBox<Party> cmbCustomer;

    @FXML
    private TextField txtItemSearch;
    @FXML private StackPane itemSearchIconBox;

    @FXML
    private TextArea txtRemarks;
    @FXML private TextArea txtBillingAddress;
    /** Displays the selected customer's billing address using the same control pattern as Create Purchase. */
    @FXML private TextArea txtDeliveryAddress;

    @FXML
    private Label lblInvoiceDisplay;

    @FXML
    private Label lblNetAmount;

    @FXML
    private Label lblGst;

    @FXML
    private Label lblDiscount;

    @FXML
    private Label lblGrandTotal;

    @FXML private Label lblTotalItems, lblBottomDiscount, lblBottomTax, lblBottomCharges, lblBottomNet, lblTaxableAmount, lblChargeCaption, lblCharges;

    @FXML
    private TableView<SalesLine> tableLines;

    @FXML
    private TableColumn<SalesLine, String> colItem;

    @FXML
    private TableColumn<SalesLine, Double> colQuantity;

    @FXML
    private TableColumn<SalesLine, Double> colRate;

    @FXML
    private TableColumn<SalesLine, Double> colGst;

    @FXML
    private TableColumn<SalesLine, Double> colDiscount;

    @FXML
    private TableColumn<SalesLine, Double> colDiscountAmount;

    @FXML
    private TableColumn<SalesLine, Double> colGstAmount;

    @FXML
    private TableColumn<SalesLine, Double> colNetAmount;

    @FXML
    private TableColumn<SalesLine, Double> colTotal;

    // Entry-toolbar columns are bound to the table's live widths so the
    // Search/Qty/Rate/Discount/GST editors remain visually aligned with
    // their corresponding headers under constrained table resizing.

    @FXML
    private Button btnAddLine;
    @FXML private Button btnRemoveLine, btnSaveDraft;
    @FXML private Button btnManageCharges;
    @FXML private Label lblChargeManagerSummary;



    @FXML
    private Button btnSaveSale;

    //-------------------------------------------------------
    // Services
    //-------------------------------------------------------

    private final ItemService itemService =
        new ItemService();

    private final PartyService partyService =
        new PartyService();

    private final SalesService salesService =
        new SalesService();

    private final LookupService lookupService = new LookupService();
    private final MasterApiClient masterApi = new MasterApiClient();

    //-------------------------------------------------------
    // Editing
    //-------------------------------------------------------

    private Sales editingSale = null;
    private boolean loadingSaleForEdit = false;

    private SalesLine editingLine = null;

    private int editingIndex = -1;
    private final ObservableList<Item> allItems = FXCollections.observableArrayList();
    private final Map<Item, String> itemSearchIndex = new IdentityHashMap<>();
    private final ContextMenu itemSuggestions = new ContextMenu();
    private final PauseTransition itemSearchDebounce = new PauseTransition(Duration.millis(160));
    private final PauseTransition customerSearchDebounce = new PauseTransition(Duration.millis(180));
    private Item selectedItem;
    private boolean updatingItemSearch;
    private boolean updatingCustomerSearch;
    private final ObservableList<SalesCharge> invoiceCharges = FXCollections.observableArrayList();
    private final ObservableList<String> availableChargeTypes = FXCollections.observableArrayList();

    //-------------------------------------------------------
    // Initialize
    //-------------------------------------------------------

    @FXML
    public void initialize() {
        if (btnAddCustomer != null) { btnAddCustomer.setGraphic(IconFactory.compactIcon("customer", 20)); btnAddCustomer.getProperties().put("erp-icon-preserve", true); }
        if (chkSameAsBilling != null) {
            // Keep this control as a conventional checkbox + label. A zero-size,
            // controller-owned graphic prevents the global action decorator from
            // inserting an additional semantic icon beside the checkbox mark.
            Region noActionIcon = new Region();
            noActionIcon.setMinSize(0, 0);
            noActionIcon.setPrefSize(0, 0);
            noActionIcon.setMaxSize(0, 0);
            chkSameAsBilling.setGraphic(noActionIcon);
            chkSameAsBilling.setGraphicTextGap(0);
            chkSameAsBilling.getProperties().put("erp-icon-preserve", true);
        }
        configureExplicitTableHeaderIcons();

        setupTable();
        configureEmptyState();

        setupAmountFormatting();

        tableLines.setEditable(true);

        setupEditableColumns();
        Platform.runLater(this::decorateActions);
        cmbSalesPerson.getItems().setAll("Admin","Ajay Shah","Rahul Mehta");cmbSalesPerson.setValue("Admin");
        if (btnManageCharges != null) {
            btnManageCharges.setGraphic(IconFactory.compactIcon("payment", 15));
            btnManageCharges.getProperties().put("erp-icon-preserve", true);
        }
        updateChargeManagerSummary();

        // PO Date follows Invoice Date + Payment Terms for new sales and when
        // either driver is changed by the user. During loadSale() we suppress
        // these listeners so the historical saved PO Date is preserved exactly.
        if (dpInvoiceDate != null) {
            dpInvoiceDate.valueProperty().addListener((o, oldDate, newDate) -> {
                if (!loadingSaleForEdit) updatePoDateFromPaymentTerms();
            });
        }
        if (cmbPaymentTerms != null) {
            cmbPaymentTerms.valueProperty().addListener((o, oldTerms, newTerms) -> {
                if (!loadingSaleForEdit) updatePoDateFromPaymentTerms();
            });
        }

        // Delivery Address can follow Billing Address or be entered independently.
        if (chkSameAsBilling != null) {
            chkSameAsBilling.selectedProperty().addListener((o, oldValue, same) -> syncDeliveryAddressState());
        }
        if (txtBillingAddress != null) {
            txtBillingAddress.textProperty().addListener((o, oldValue, address) -> {
                if (chkSameAsBilling != null && chkSameAsBilling.isSelected()) {
                    txtDeliveryAddress.setText(address == null ? "" : address);
                }
            });
        }
        if (txtBillingGstin != null) {
            txtBillingGstin.textProperty().addListener((o, oldValue, gstin) -> {
                if (chkSameAsBilling != null && chkSameAsBilling.isSelected()) {
                    txtDeliveryGstin.setText(gstin == null ? "" : gstin);
                }
            });
        }

        // Master-driven values are loaded in the background below. Configure
        // the selector/listeners immediately so the page is interactive before
        // any API-backed master data arrives.
        configureTransporterSelector();
        cmbGstType.valueProperty().addListener((o,a,b) -> updateGstHeaders());
        updateGstHeaders();

        invoiceCharges.addListener((javafx.collections.ListChangeListener<SalesCharge>) change -> {updateChargeManagerSummary();recalculate();});

        tableLines.getSelectionModel()
            .selectedItemProperty()
            .addListener((obs, oldLine, newLine) -> {

                if (newLine == null)
                    return;

                editingLine = newLine;

                editingIndex =
                    tableLines.getSelectionModel()
                        .getSelectedIndex();

                txtQuantity.setText(
                    String.valueOf(
                        newLine.getQuantity()));

                txtRate.setText(
                    String.valueOf(
                        newLine.getRate()));

                txtGST.setText(
                    String.valueOf(
                        newLine.getGstPercent()));

                txtLineDiscount.setText(String.valueOf(newLine.getDiscountPercent()));

                Item cachedItem = allItems.stream()
                    .filter(item -> safeItem(item.getItemCode()).equalsIgnoreCase(safeItem(newLine.getItemCode())))
                    .findFirst().orElse(null);
                if (cachedItem != null) {
                    selectItem(cachedItem);
                } else if (!safeItem(newLine.getItemCode()).isBlank()) {
                    String code = newLine.getItemCode();
                    UiTaskExecutor.submitLatest(
                        "create-sale-line-item-lookup",
                        () -> itemService.search(code, 12),
                        matches -> matches.stream()
                            .filter(item -> safeItem(item.getItemCode()).equalsIgnoreCase(safeItem(code)))
                            .findFirst()
                            .ifPresent(item -> { mergeItemCache(List.of(item)); selectItem(item); }),
                        error -> System.err.println("Create Sale line item lookup: " + rootMessage(error))
                    );
                }

            });

        //-------------------------------------------------------
        // Customer Combo
        //-------------------------------------------------------

        cmbCustomer.setCellFactory(list ->
            new ListCell<>() {

                @Override
                protected void updateItem(
                    Party party,
                    boolean empty) {

                    super.updateItem(party, empty);

                    setText(

                        empty || party == null

                            ? null

                            : party.getPartyCode()
                              + " - "
                              + party.getName()

                    );

                }

            });

        cmbCustomer.setButtonCell(
            new ListCell<>() {

                @Override
                protected void updateItem(
                    Party party,
                    boolean empty) {

                    super.updateItem(party, empty);

                    setText(

                        empty || party == null

                            ? null

                            : party.getPartyCode()
                              + " - "
                              + party.getName()

                    );

                }

            });

        cmbCustomer.setEditable(true);
        cmbCustomer.setConverter(new StringConverter<>() {
            @Override public String toString(Party party) { return party == null ? "" : safeParty(party.getPartyCode()) + " - " + safeParty(party.getName()); }
            @Override public Party fromString(String text) {
                if (text == null || text.isBlank()) return null;
                return cmbCustomer.getItems().stream().filter(p -> customerDisplay(p).equalsIgnoreCase(text.trim()) || safeParty(p.getPartyCode()).equalsIgnoreCase(text.trim()) || safeParty(p.getName()).equalsIgnoreCase(text.trim())).findFirst().orElse(null);
            }
        });
        customerSearchDebounce.setOnFinished(event -> searchCustomers(cmbCustomer.getEditor().getText()));
        cmbCustomer.getEditor().textProperty().addListener((obs, oldText, text) -> {
            if (updatingCustomerSearch || !cmbCustomer.getEditor().isFocused()) return;
            Party committed = cmbCustomer.getValue();
            if (committed != null && customerDisplay(committed).equalsIgnoreCase(safeParty(text))) {
                customerSearchDebounce.stop();
                return;
            }
            customerSearchDebounce.playFromStart();
        });
        cmbCustomer.showingProperty().addListener((obs, oldValue, showing) -> {
            if (showing && cmbCustomer.getItems().isEmpty()) searchCustomers("");
        });

        cmbCustomer.valueProperty().addListener((observable, oldCustomer, customer) -> {
            // loadSale() and the asynchronous bootstrap both re-bind persisted
            // edit selections. During that re-bind the saved invoice addresses
            // must not be replaced by today's master-data address.
            if (loadingSaleForEdit && editingSale != null) return;
            if (customer != null) {
                customerSearchDebounce.stop();
                UiTaskExecutor.cancel("create-sale-customer-search");
                updatingCustomerSearch = true;
                try {
                    String display = customerDisplay(customer);
                    if (!display.equals(cmbCustomer.getEditor().getText())) cmbCustomer.getEditor().setText(display);
                } finally { updatingCustomerSearch = false; }
            }
            if (customer == null) {
                if (cmbCustomer.isEditable() && cmbCustomer.getEditor().isFocused() && !cmbCustomer.getEditor().getText().isBlank()) return;
                txtBillingAddress.clear();
                if (txtBillingGstin != null) txtBillingGstin.clear();
                if (txtDeliveryGstin != null) txtDeliveryGstin.clear();
                if (editingSale == null && txtDeliveryAddress != null) txtDeliveryAddress.clear();
                return;
            }
            String address = customer.getAddress() == null ? "" : customer.getAddress().trim();
            txtBillingAddress.setText(address);
            if (txtBillingGstin != null && (editingSale == null || txtBillingGstin.getText() == null || txtBillingGstin.getText().isBlank())) {
                txtBillingGstin.setText(customer.getGstin() == null ? "" : customer.getGstin());
            }
            if (editingSale == null && chkSameAsBilling != null) chkSameAsBilling.setSelected(true);
            syncDeliveryAddressState();
            suggestGstTypeFromGstin();
        });

        //-------------------------------------------------------
        // Item Combo
        //-------------------------------------------------------

        configureItemSearch();

        // The form itself is created immediately. API-backed master data and the
        // next invoice number are loaded away from the JavaFX Application Thread.
        newSale();
        loadSaleBootstrapAsync();

    }

    private void loadSaleBootstrapAsync() {
        UiTaskExecutor.submitLatest(
            "create-sale-bootstrap",
            this::loadSaleBootstrap,
            this::applySaleBootstrap,
            this::handleSaleBootstrapFailure
        );
    }

    private SaleBootstrap loadSaleBootstrap() {
        List<String> errors = new ArrayList<>();
        List<String> paymentTerms = List.of(), charges = List.of(), gstTypes = List.of();
        List<Lookup> transporters = List.of(); List<Party> customers = List.of();
        try {
            if (ConfigManager.isApiDataEnabled()) {
                MasterApiClient.SalesEntryBootstrap master = masterApi.salesEntryBootstrap();
                paymentTerms = master.paymentTerms()==null?List.of():master.paymentTerms();
                charges = master.chargeTypes()==null?List.of():master.chargeTypes();
                gstTypes = master.gstTypes()==null?List.of():master.gstTypes();
                transporters = master.transporters()==null?List.of():master.transporters();
                customers = master.customers()==null?List.of():master.customers();
            } else {
                paymentTerms = lookupService.getValuesByCategoryCode("PAYMENT_TERMS");
                charges = lookupService.getValuesByCategoryCode("CHARGES");
                gstTypes = lookupService.getValuesByCategoryCode("GST_TYPE");
                transporters = lookupService.getByCategoryCode("TRANSPORTER");
                customers = partyService.search("CUSTOMER","",40);
            }
        } catch (Exception exception) {
            errors.add("Master bootstrap: " + rootMessage(exception));
            // Keep Create Sale usable if an older/local server does not yet expose
            // the consolidated bootstrap endpoint. These fallbacks still execute
            // on UiTaskExecutor, never on the JavaFX Application Thread.
            paymentTerms = loadOrDefault("Payment Terms", errors, () -> lookupService.getValuesByCategoryCode("PAYMENT_TERMS"), List.of());
            charges = loadOrDefault("Charges", errors, () -> lookupService.getValuesByCategoryCode("CHARGES"), List.of());
            gstTypes = loadOrDefault("GST Types", errors, () -> lookupService.getValuesByCategoryCode("GST_TYPE"), List.of());
            transporters = loadOrDefault("Transporters", errors, () -> lookupService.getByCategoryCode("TRANSPORTER"), List.of());
            customers = loadOrDefault("Customers", errors, () -> partyService.search("CUSTOMER", "", 40), List.of());
        }
        String invoiceNo = loadOrDefault("Next Invoice No", errors, salesService::nextInvoiceNo, "");
        return new SaleBootstrap(paymentTerms, charges, gstTypes, transporters, customers, invoiceNo, List.copyOf(errors));
    }

    private <T> T loadOrDefault(String label, List<String> errors, Supplier<T> loader, T fallback) {
        try {
            T value = loader.get();
            return value == null ? fallback : value;
        } catch (Exception exception) {
            errors.add(label + ": " + rootMessage(exception));
            return fallback;
        }
    }

    private void applySaleBootstrap(SaleBootstrap bootstrap) {
        if (bootstrap == null) return;

        cmbPaymentTerms.getItems().setAll(bootstrap.paymentTerms());
        availableChargeTypes.setAll(bootstrap.chargeTypes());
        if (cmbChargeType != null) cmbChargeType.getItems().setAll(availableChargeTypes);
        cmbGstType.getItems().setAll(bootstrap.gstTypes());
        cmbTransporter.getItems().setAll(bootstrap.transporters());
        cmbCustomer.setItems(FXCollections.observableArrayList(bootstrap.customers()));

        if (editingSale == null) {
            selectDefaultPaymentTerms();
            if (!cmbGstType.getItems().isEmpty()) cmbGstType.getSelectionModel().selectFirst();
            if (!bootstrap.invoiceNo().isBlank()) {
                txtInvoiceNo.setText(bootstrap.invoiceNo());
                if (lblInvoiceDisplay != null) lblInvoiceDisplay.setText(bootstrap.invoiceNo());
            } else if ("Loading...".equals(txtInvoiceNo.getText())) {
                txtInvoiceNo.clear();
            }
            updatePoDateFromPaymentTerms();
        } else {
            // loadSale() can run immediately after FXMLLoader.load(). If master
            // data arrives later, re-bind selections to the persisted edit values
            // without replacing historical addresses, dates or line items.
            boolean previousLoadingState = loadingSaleForEdit;
            loadingSaleForEdit = true;
            try {
                String terms = editingSale.getPaymentTerms();
                cmbPaymentTerms.setValue(terms == null || terms.isBlank() ? "15 Days" : terms);
                String gstType = editingSale.getGstType();
                if (gstType != null && !gstType.isBlank()) cmbGstType.setValue(gstType);
                if (editingSale.getTransporter() != null && !editingSale.getTransporter().isBlank()) {
                    cmbTransporter.getItems().stream()
                        .filter(value -> value.getLookupValue().equalsIgnoreCase(editingSale.getTransporter()))
                        .findFirst().ifPresent(cmbTransporter::setValue);
                }
                if (editingSale.getCustomer() != null) {
                    int customerId = editingSale.getCustomer().getId();
                    Party loadedCustomer = cmbCustomer.getItems().stream()
                        .filter(party -> party.getId() == customerId)
                        .findFirst().orElse(null);
                    if (loadedCustomer != null) cmbCustomer.setValue(loadedCustomer);
                    else {
                        String customerQuery = editingSale.getCustomer().getPartyCode();
                        UiTaskExecutor.submitLatest("create-sale-edit-customer-lookup",
                            () -> partyService.search("CUSTOMER", customerQuery, 20),
                            customers -> customers.stream().filter(party -> party.getId() == customerId).findFirst().ifPresent(party -> { cmbCustomer.getItems().add(party); cmbCustomer.setValue(party); }),
                            error -> System.err.println("Create Sale edit customer lookup: " + rootMessage(error)));
                    }
                }
            } finally {
                loadingSaleForEdit = previousLoadingState;
            }
        }

        if (!bootstrap.errors().isEmpty()) showSaleBootstrapWarning(bootstrap.errors());
    }

    private void handleSaleBootstrapFailure(Throwable error) {
        showSaleBootstrapWarning(List.of("Startup: " + rootMessage(error)));
    }

    private void showSaleBootstrapWarning(List<String> errors) {
        if (errors == null || errors.isEmpty()) return;
        System.err.println("Create Sale initialization: " + String.join(" | ", errors));
        new OwnedAlert(Alert.AlertType.WARNING,
            "Create Sale opened, but some API-backed master data could not be loaded.\n\n" +
            String.join("\n", errors) +
            "\n\nCheck that the Spring server is running and review its console for the matching endpoint error.")
            .showAndWait();
    }

    private record SaleBootstrap(
        List<String> paymentTerms,
        List<String> chargeTypes,
        List<String> gstTypes,
        List<Lookup> transporters,
        List<Party> customers,
        String invoiceNo,
        List<String> errors
    ) { }

    /**
     * Suggests intra-state versus inter-state tax from the first two GSTIN
     * digits. This runs only when a customer is selected, so the user can still
     * override the suggested GST type afterwards.
     */
    private void suggestGstTypeFromGstin() {
        if (cmbGstType == null || cmbGstType.getItems().isEmpty() || txtBillingGstin == null) return;
        String companyGstin = ConfigManager.get("company.gstin", "").trim();
        String customerGstin = txtBillingGstin.getText() == null ? "" : txtBillingGstin.getText().trim();
        if (companyGstin.length() < 2 || customerGstin.length() < 2
                || !companyGstin.substring(0, 2).matches("\\d{2}")
                || !customerGstin.substring(0, 2).matches("\\d{2}")) return;

        boolean interstate = !companyGstin.substring(0, 2).equals(customerGstin.substring(0, 2));
        cmbGstType.getItems().stream()
                .filter(value -> {
                    String normalized = value == null ? "" : value.toUpperCase(java.util.Locale.ROOT);
                    return interstate
                            ? normalized.contains("IGST") || normalized.contains("INTER")
                            : normalized.contains("CGST") || normalized.contains("SGST") || normalized.contains("INTRA");
                })
                .findFirst()
                .ifPresent(cmbGstType::setValue);
    }

    private void configureItemSearch() {
        if (itemSearchIconBox != null) itemSearchIconBox.getChildren().setAll(IconFactory.compactIcon("search", 16));
        itemSuggestions.getStyleClass().addAll("sales-entry-item-suggestions","erp-item-suggestions");
        itemSearchDebounce.setOnFinished(event -> refreshItemSuggestions(txtItemSearch.getText()));
        txtItemSearch.textProperty().addListener((obs, oldText, text) -> {
            if (updatingItemSearch) return;
            selectedItem = null;
            // Rebuilding a ContextMenu (including icons) for every physical
            // keystroke is expensive on high-DPI displays. A short debounce keeps
            // typing immediate while still feeling instant to the user.
            itemSearchDebounce.playFromStart();
        });
        txtItemSearch.focusedProperty().addListener((obs, oldValue, focused) -> {
            if (focused && !txtItemSearch.getText().isBlank()) refreshItemSuggestions(txtItemSearch.getText());
            else if (!focused) itemSuggestions.hide();
        });
        txtItemSearch.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) itemSuggestions.hide();
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Item match = resolveTypedItem(txtItemSearch.getText());
                if (match != null) selectItem(match);
            }
        });
    }

    private void refreshItemSuggestions(String text) {
        String query = text == null ? "" : text.trim();
        if (query.isBlank() || !txtItemSearch.isFocused()) { itemSuggestions.hide(); return; }
        UiTaskExecutor.submitLatest(
            "create-sale-item-search",
            () -> itemService.search(query, 12),
            matches -> {
                if (!txtItemSearch.isFocused() || !safeItem(txtItemSearch.getText()).equalsIgnoreCase(query)) return;
                mergeItemCache(matches);
                itemSuggestions.getItems().clear();
                for (Item item : matches) {
                    MenuItem option = new MenuItem(itemSearchDisplay(item), IconFactory.compactIcon("item", 15));
                    option.setOnAction(event -> selectItem(item));
                    itemSuggestions.getItems().add(option);
                }
                if (matches.isEmpty()) itemSuggestions.hide();
                else if (!itemSuggestions.isShowing()) itemSuggestions.show(txtItemSearch, Side.BOTTOM, 0, 2);
            },
            error -> System.err.println("Create Sale item search: " + rootMessage(error))
        );
    }

    private void mergeItemCache(List<Item> items) {
        if (items == null) return;
        for (Item item : items) {
            Item existing = allItems.stream().filter(x -> safeItem(x.getItemCode()).equalsIgnoreCase(safeItem(item.getItemCode()))).findFirst().orElse(null);
            if (existing != null) { allItems.remove(existing); itemSearchIndex.remove(existing); }
            allItems.add(item); itemSearchIndex.put(item, buildItemSearchHaystack(item));
        }
    }

    private void searchCustomers(String text) {
        String query = text == null ? "" : text.trim();
        UiTaskExecutor.submitLatest(
            "create-sale-customer-search",
            () -> partyService.search("CUSTOMER", query, 30),
            customers -> {
                if (cmbCustomer.getEditor().isFocused() && !safeParty(cmbCustomer.getEditor().getText()).equalsIgnoreCase(query)) return;
                Party selected = cmbCustomer.getValue();
                updatingCustomerSearch = true;
                try {
                    java.util.List<Party> stable = new java.util.ArrayList<>(customers);
                    if (selected != null && stable.stream().noneMatch(party -> party.getId() == selected.getId())) stable.add(0, selected);
                    cmbCustomer.getItems().setAll(stable);
                    if (selected != null) cmbCustomer.getItems().stream().filter(party -> party.getId() == selected.getId()).findFirst().ifPresent(cmbCustomer::setValue);
                } finally { updatingCustomerSearch = false; }
                if (!customers.isEmpty() && cmbCustomer.getEditor().isFocused() && !cmbCustomer.isShowing()) cmbCustomer.show();
            },
            error -> System.err.println("Create Sale customer search: " + rootMessage(error))
        );
    }

    private static String safeParty(String value) { return value == null ? "" : value.trim(); }
    private static String customerDisplay(Party party) { return party == null ? "" : safeParty(party.getPartyCode()) + " - " + safeParty(party.getName()); }

    private void selectItem(Item item) {
        selectedItem = item;
        updatingItemSearch = true;
        try { txtItemSearch.setText(item == null ? "" : itemSearchDisplay(item)); }
        finally { updatingItemSearch = false; }
        itemSuggestions.hide();
        if (item != null) {
            txtRate.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getSellingPrice()));
            txtGST.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getGst()));
            txtLineDiscount.setText(String.format(java.util.Locale.ROOT, "%.2f", item.getDiscountPercent()));
        }
    }

    private void clearItemSearch() { selectItem(null); }

    private Item resolveTypedItem(String text) {
        if (selectedItem != null) return selectedItem;
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) return null;
        return allItems.stream().filter(item ->
            itemSearchDisplay(item).equalsIgnoreCase(value)
                || safeItem(item.getItemCode()).equalsIgnoreCase(value)
                || safeItem(item.getDescription()).equalsIgnoreCase(value)
                || itemRemark(item).equalsIgnoreCase(value)
        ).findFirst().orElse(null);
    }

    private String itemSearchHaystack(Item item) {
        String indexed = itemSearchIndex.get(item);
        if (indexed != null) return indexed;
        return buildItemSearchHaystack(item);
    }

    private String buildItemSearchHaystack(Item item) {
        return (safeItem(item.getItemCode()) + " " + safeItem(item.getDescription()) + " "
            + safeItem(item.getRemarks()) + " " + safeItem(item.getHsn())).toLowerCase(java.util.Locale.ROOT);
    }

    private String itemSearchDisplay(Item item) {
        if (item == null) return "";
        String remark = itemRemark(item);
        String description = safeItem(item.getDescription());
        if (remark.isBlank()) return description;
        if (description.isBlank()) return remark;
        return remark + " • " + description;
    }

    private String itemRemark(Item item) {
        if (item == null) return "";
        return safeItem(item.getRemarks()).trim();
    }

    private String safeItem(String value) { return value == null ? "" : value.trim(); }

    private String itemNameForDisplay(String itemCode, String persistedDescription) {
        String code = safeItem(itemCode);
        if (!code.isBlank()) {
            for (Item item : allItems) {
                if (code.equalsIgnoreCase(safeItem(item.getItemCode()))) {
                    String name = itemSearchDisplay(item);
                    if (!name.isBlank()) return name;
                }
            }
        }
        String fallback = safeItem(persistedDescription);
        int separator = fallback.indexOf(" - ");
        return separator >= 0 && separator + 3 < fallback.length()
            ? fallback.substring(separator + 3).trim() : fallback;
    }

    private void configureTransporterSelector() {
        cmbTransporter.setConverter(new StringConverter<>() {
            @Override public String toString(Lookup lookup) { return lookup == null ? "" : lookup.getLookupValue(); }
            @Override public Lookup fromString(String text) { return null; }
        });
        cmbTransporter.valueProperty().addListener((o, oldValue, lookup) -> {
            if (txtTransporterGstin != null) {
                txtTransporterGstin.setText(lookup == null || lookup.getDescription() == null ? "" : lookup.getDescription().trim());
            }
        });
    }

    private void updateGstHeaders() {
        String type = cmbGstType == null ? "" : cmbGstType.getValue();
        boolean igst = type != null && type.trim().equalsIgnoreCase("IGST");
        String percentLabel = igst ? "IGST %" : "GST %";
        String amountLabel = igst ? "IGST Amount (₹)" : "GST Amount (₹)";
        if (colGst != null) colGst.setText(percentLabel);
        if (colGstAmount != null) colGstAmount.setText(amountLabel);
        if (txtGST != null) txtGST.setPromptText(percentLabel);
    }

    /** Opens the shared themed customer editor and refreshes the sale form. */
    @FXML
    private void addCustomer() {
        try {
            FXMLLoader loader = new FXMLLoader(org.example.util.ResourceLocator.require("/fxml/pages/PartyDialog.fxml"));
            Parent root = loader.load(); org.example.util.ProfessionalUiEnhancer.enhance(root);
            loader.<PartyDialogController>getController().configure("CUSTOMER", null);
            Stage dialog = new Stage();
            PlatformUiSupport.configureDialogStage(dialog, cmbCustomer, "Add Customer", true);
            Scene scene = new Scene(root);
            ThemeManager.applyTheme(scene);
            dialog.setScene(scene);
            dialog.showAndWait();
            Party selected = cmbCustomer.getValue();
            cmbCustomer.getItems().setAll(partyService.search("CUSTOMER","",40));
            if (selected != null) cmbCustomer.getSelectionModel().select(selected);
        } catch (Exception ex) {
            new OwnedAlert(Alert.AlertType.ERROR,
                "Unable to open customer form: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    //-------------------------------------------------------
    // Setup Table
    //-------------------------------------------------------


    private void setupTable() {

        colItem.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
            itemNameForDisplay(value.getValue().getItemCode(), value.getValue().getItemDescription())));

        colQuantity.setCellValueFactory(
            new PropertyValueFactory<>("quantity"));

        colRate.setCellValueFactory(
            new PropertyValueFactory<>("rate"));

        colGst.setCellValueFactory(
            new PropertyValueFactory<>("gstPercent"));

        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
        colDiscountAmount.setCellValueFactory(new PropertyValueFactory<>("discountAmount"));

        colGstAmount.setCellValueFactory(
            new PropertyValueFactory<>("gstAmount"));

        colNetAmount.setCellValueFactory(
            new PropertyValueFactory<>("netAmount"));

        colTotal.setCellValueFactory(
            new PropertyValueFactory<>("totalAmount"));

    }

    //-------------------------------------------------------
    // Amount Formatting
    //-------------------------------------------------------

    private void setupAmountFormatting() {

        colQuantity.setCellFactory(column ->
            new TextFieldTableCell<>(
                new DoubleStringConverter()
            ));

        colRate.setCellFactory(column ->
            new TextFieldTableCell<>(
                new DoubleStringConverter()
            ));

        colGst.setCellFactory(column ->
            new TextFieldTableCell<>(
                new DoubleStringConverter()
            ));

        colDiscount.setCellFactory(column ->
            new TextFieldTableCell<>(new DoubleStringConverter()));

        colDiscountAmount.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("₹ %.2f", value));
            }
        });

        colGstAmount.setCellFactory(column ->
            new TableCell<>() {

                @Override
                protected void updateItem(Double value, boolean empty) {

                    super.updateItem(value, empty);

                    if (empty || value == null) {

                        setText(null);

                    } else {

                        setText(
                            String.format("₹ %.2f", value)
                        );

                    }

                }

            });

        colNetAmount.setCellFactory(column ->
            new TableCell<>() {

                @Override
                protected void updateItem(Double value, boolean empty) {

                    super.updateItem(value, empty);

                    if (empty || value == null) {

                        setText(null);

                    } else {

                        setText(
                            String.format("₹ %.2f", value)
                        );

                    }

                }

            });

        colTotal.setCellFactory(column ->
            new TableCell<>() {

                @Override
                protected void updateItem(Double value, boolean empty) {

                    super.updateItem(value, empty);

                    if (empty || value == null) {

                        setText(null);

                    } else {

                        setText(
                            String.format("₹ %.2f", value)
                        );

                    }

                }

            });

    }

    //-------------------------------------------------------
    // Editable Columns
    //-------------------------------------------------------

    private void setupEditableColumns() {

        colQuantity.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            ));

        colQuantity.setOnEditCommit(event -> {

            SalesLine line = event.getRowValue();

            line.setQuantity(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });

        colRate.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            ));

        colRate.setOnEditCommit(event -> {

            SalesLine line = event.getRowValue();

            line.setRate(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });

        colGst.setCellFactory(
            TextFieldTableCell.forTableColumn(
                new DoubleStringConverter()
            ));

        colGst.setOnEditCommit(event -> {

            SalesLine line = event.getRowValue();

            line.setGstPercent(event.getNewValue());

            recalculateLine(line);

            tableLines.refresh();

            recalculate();

        });

        colDiscount.setOnEditCommit(event -> {
            SalesLine line = event.getRowValue();
            double value = event.getNewValue() == null ? 0 : event.getNewValue();
            line.setDiscountPercent(Math.max(0, Math.min(100, value)));
            recalculateLine(line);
            tableLines.refresh();
            recalculate();
        });

    }

    //-------------------------------------------------------
    // Recalculate One Line
    //-------------------------------------------------------

    private void recalculateLine(SalesLine line) {

        line.recalculate();

    }

    //--------------------------------------------------
// SAVE SALE
//--------------------------------------------------

    @FXML
    private void saveSale() {

        Sales sale = buildSale();

        if (sale == null)
            return;

        try {

            if (editingSale != null) {

                sale.setId(editingSale.getId());

                salesService.update(sale);

                NotificationService.add(
                    "Sales "
                        + sale.getInvoiceNo()
                        + " updated"
                );

            } else {

                salesService.save(sale);

                NotificationService.add(
                    "Sales "
                        + sale.getInvoiceNo()
                        + " saved"
                );

            }

            new OwnedAlert(
                Alert.AlertType.INFORMATION,
                "Sales saved successfully"
            ).showAndWait();

            NavigationManager.getInstance()
                .loadPage("/fxml/pages/SalesList.fxml");

        }
        catch (Exception e) {

            new OwnedAlert(
                Alert.AlertType.ERROR,
                e.getMessage()
            ).showAndWait();

        }

    }


//--------------------------------------------------
// BUILD SALES OBJECT
//--------------------------------------------------

    private Sales buildSale() {

        if (dpInvoiceDate.getValue() == null) {

            warn("Select invoice date");

            return null;

        }

        if (cmbCustomer.getValue() == null) {

            warn("Select customer");

            return null;

        }

        if (txtDeliveryAddress != null && (txtDeliveryAddress.getText() == null || txtDeliveryAddress.getText().isBlank())) {
            warn("Enter delivery address");
            return null;
        }
        if (chkSameAsBilling != null && !chkSameAsBilling.isSelected()
            && normalized(txtBillingAddress == null ? "" : txtBillingAddress.getText())
                .equals(normalized(txtDeliveryAddress == null ? "" : txtDeliveryAddress.getText()))
            && normalized(txtBillingGstin == null ? "" : txtBillingGstin.getText())
                .equals(normalized(txtDeliveryGstin == null ? "" : txtDeliveryGstin.getText()))) {
            warn("Delivery address and GSTIN still match billing details. Select 'Same as Billing Address' or update the delivery details.");
            return null;
        }

        if (tableLines.getItems().isEmpty()) {

            warn("Add items");

            return null;

        }

        Sales sale = new Sales();

        // Preserve persisted workflow state during Edit -> Save.  This screen owns
        // invoice/header details, lines and charges, but it must not silently reset
        // payment/communication/status fields that are managed elsewhere.
        if (editingSale != null) {
            sale.setId(editingSale.getId());
            sale.setCreatedAt(editingSale.getCreatedAt());
            sale.setPaidAmount(editingSale.getPaidAmount());
            sale.setPaymentStatus(editingSale.getPaymentStatus());
            sale.setEmailSent(editingSale.isEmailSent());
            sale.setWhatsappSent(editingSale.isWhatsappSent());
            sale.setInvoiceType(editingSale.getInvoiceType());
            sale.setSource(editingSale.getSource());
            sale.setDocumentStatus(editingSale.getDocumentStatus());
        }

        sale.setInvoiceNo(
            txtInvoiceNo.getText()
        );

        sale.setInvoiceDate(
            dpInvoiceDate.getValue()
        );

        sale.setCustomer(
            cmbCustomer.getValue()
        );

        sale.setLines(
            List.copyOf(
                tableLines.getItems()
            )
        );

        double net =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getNetAmount
                )
                .sum();

        double discount = tableLines.getItems().stream().mapToDouble(SalesLine::getDiscountAmount).sum();

        double gst =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getGstAmount
                )
                .sum();

        String chargeError = validateCharges(invoiceCharges);
        if (chargeError != null) { warn(chargeError); return null; }
        double charges = invoiceCharges.stream().mapToDouble(SalesCharge::getAmount).sum();
        double chargeTax = invoiceCharges.stream().mapToDouble(SalesCharge::getTaxAmount).sum();
        double total = net + gst + charges + chargeTax;

        sale.setSubtotal(net);
        sale.setDiscountAmount(discount);

        sale.setGstAmount(gst + chargeTax);

        sale.setTotalAmount(total);

        sale.setRemarks(txtRemarks == null ? "" : txtRemarks.getText());
        sale.setDueDate(calculatePaymentDueDate(dpInvoiceDate.getValue(), cmbPaymentTerms.getValue()));
        String poDateText = txtPoDate == null ? "" : txtPoDate.getText();
        sale.setPoDate(poDateText == null || poDateText.isBlank() ? null : BusinessClock.parseDate(poDateText));
        sale.setOrderNo(txtOrderNo == null ? "" : txtOrderNo.getText());
        sale.setSalesperson(cmbSalesPerson.getValue());
        sale.setNotes(txtInvoiceMessage == null || txtInvoiceMessage.getText() == null ? "" : txtInvoiceMessage.getText());
        String billing = txtBillingAddress == null ? "" : txtBillingAddress.getText();
        String shipping = txtDeliveryAddress == null ? "" : txtDeliveryAddress.getText();
        sale.setBillingAddress(billing == null ? "" : billing);
        sale.setDeliveryAddress(shipping == null ? "" : shipping);
        String billingGstin = txtBillingGstin == null ? "" : txtBillingGstin.getText();
        sale.setGstin(billingGstin); // Legacy compatibility: GSTIN remains the billing GSTIN.
        sale.setBillingGstin(billingGstin);
        sale.setDeliveryGstin(txtDeliveryGstin == null ? "" : txtDeliveryGstin.getText());
        sale.setSameAsBilling(chkSameAsBilling != null && chkSameAsBilling.isSelected());
        sale.setTransporterGstin(txtTransporterGstin == null ? "" : txtTransporterGstin.getText());
        sale.setPaymentTerms(cmbPaymentTerms.getValue());
        sale.setGstType(cmbGstType == null ? "" : cmbGstType.getValue());
        sale.setTransporter(cmbTransporter == null || cmbTransporter.getValue() == null ? "" : cmbTransporter.getValue().getLookupValue());
        sale.setDoorDelivery(editingSale == null ? "" : editingSale.getDoorDelivery());
        sale.setVehicleNumber(txtVehicleNumber == null ? "" : txtVehicleNumber.getText());
        sale.setContactPerson(txtContactPerson == null ? "" : txtContactPerson.getText());
        sale.setContactPersonMobile(txtContactPersonMobile == null ? "" : txtContactPersonMobile.getText());
        sale.setCharges(invoiceCharges);
        sale.setTransportNote(txtTransportNote == null ? "" : txtTransportNote.getText());
        sale.setReferenceNo(txtReference == null || txtReference.getText() == null ? "" : txtReference.getText());

        return sale;

    }


//--------------------------------------------------
// NEW SALE
//--------------------------------------------------

    private LocalDate calculatePaymentDueDate(LocalDate invoiceDate, String terms) {
        if (invoiceDate == null) return null;
        if (terms == null || terms.isBlank() || terms.equalsIgnoreCase("Due on Receipt")) return invoiceDate;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(terms);
        return matcher.find() ? invoiceDate.plusDays(Integer.parseInt(matcher.group(1))) : invoiceDate;
    }

    private void updatePoDateFromPaymentTerms() {
        if (txtPoDate == null) return;
        LocalDate invoiceDate = dpInvoiceDate == null ? null : dpInvoiceDate.getValue();
        String terms = cmbPaymentTerms == null ? null : cmbPaymentTerms.getValue();
        txtPoDate.setText(BusinessClock.formatDate(calculatePaymentDueDate(invoiceDate, terms)));
    }

    /**
     * New Sales always begin from a deterministic payment-term state before PO
     * Date is calculated. This avoids an initialization-order race where the
     * ComboBox can display its default after the first calculation already ran.
     */
    private void selectDefaultPaymentTerms() {
        if (cmbPaymentTerms == null) return;
        if (cmbPaymentTerms.getItems().contains("15 Days")) {
            cmbPaymentTerms.setValue("15 Days");
        } else if (!cmbPaymentTerms.getItems().isEmpty()) {
            cmbPaymentTerms.getSelectionModel().selectFirst();
        }
    }

    private static boolean isLegacyGeneratedPoOrderNo(String value) {
        return value != null && value.trim().matches("(?i)^PO/\\d{2}-\\d{2}-\\d{4}/\\d{4}$");
    }

    private void syncDeliveryAddressState() {
        if (txtDeliveryAddress == null) return;
        boolean same = chkSameAsBilling != null && chkSameAsBilling.isSelected();
        if (same) {
            String billing = txtBillingAddress == null ? "" : txtBillingAddress.getText();
            txtDeliveryAddress.setText(billing == null ? "" : billing);
            String billingGstin = txtBillingGstin == null ? "" : txtBillingGstin.getText();
            if (txtDeliveryGstin != null) txtDeliveryGstin.setText(billingGstin == null ? "" : billingGstin);
        }
        txtDeliveryAddress.setEditable(!same);
        txtDeliveryAddress.setDisable(false);
        txtDeliveryAddress.setOpacity(same ? 0.88 : 1.0);
        if (txtDeliveryGstin != null) {
            txtDeliveryGstin.setEditable(!same);
            txtDeliveryGstin.setDisable(false);
            txtDeliveryGstin.setOpacity(same ? 0.88 : 1.0);
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void newSale() {

        editingSale = null;

        // Invoice numbering is API-backed and is loaded by loadSaleBootstrapAsync().
        // Never block the JavaFX Application Thread while the screen is opening.
        txtInvoiceNo.setText("Loading...");

        // Establish both drivers first, then calculate PO Date. The order is
        // intentional: PO Date must never be calculated against a null/stale
        // payment term when a fresh Sales screen is opened.
        loadingSaleForEdit = true;
        selectDefaultPaymentTerms();
        dpInvoiceDate.setValue(BusinessClock.today());
        loadingSaleForEdit = false;
        updatePoDateFromPaymentTerms();

        cmbCustomer.setValue(null);

        clearItemSearch();

        txtQuantity.clear();

        txtRate.clear();

        txtGST.clear();
        txtLineDiscount.clear();

        txtRemarks.clear();
        if (txtInvoiceMessage != null) txtInvoiceMessage.clear();
        if (txtReference != null) txtReference.clear();
        txtBillingAddress.clear();
        txtDeliveryAddress.clear();
        if (chkSameAsBilling != null) chkSameAsBilling.setSelected(true);
        if (txtOrderNo != null) txtOrderNo.clear();
        if (txtBillingGstin != null) txtBillingGstin.clear();
        if (txtDeliveryGstin != null) txtDeliveryGstin.clear();
        if (txtTransporterGstin != null) txtTransporterGstin.clear();
        if (cmbGstType != null && !cmbGstType.getItems().isEmpty()) cmbGstType.getSelectionModel().selectFirst();
        if (cmbTransporter != null) cmbTransporter.setValue(null);
        if (txtVehicleNumber != null) txtVehicleNumber.clear();
        if (txtContactPerson != null) txtContactPerson.clear();
        if (txtContactPersonMobile != null) txtContactPersonMobile.clear();
        if (txtTransportNote != null) txtTransportNote.clear();
        invoiceCharges.clear();

        tableLines.getItems().clear();

        recalculate();

    }
    //--------------------------------------------------
// RECALCULATE TOTALS
//--------------------------------------------------

    private void recalculate() {

        double net =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getNetAmount
                )
                .sum();

        double discount = tableLines.getItems().stream().mapToDouble(SalesLine::getDiscountAmount).sum();

        double gst =
            tableLines.getItems()
                .stream()
                .mapToDouble(
                    SalesLine::getGstAmount
                )
                .sum();

        double charges = invoiceCharges.stream().mapToDouble(SalesCharge::getAmount).sum();
        double chargeTax = invoiceCharges.stream().mapToDouble(SalesCharge::getTaxAmount).sum();
        double total = net + gst + charges + chargeTax;

        lblNetAmount.setText(
            String.format("₹ %.2f", net)
        );

        lblDiscount.setText(String.format("₹ %.2f", discount));

        lblGst.setText(
            String.format("₹ %.2f", gst + chargeTax)
        );

        lblGrandTotal.setText(
            String.format("₹ %.2f", total)
        );

        if (lblTotalItems != null) lblTotalItems.setText(Integer.toString(tableLines.getItems().size()));
        if (lblBottomDiscount != null) lblBottomDiscount.setText(String.format("₹ %.2f", discount));
        if (lblBottomTax != null) lblBottomTax.setText(String.format("₹ %.2f", gst + chargeTax));
        if (lblBottomCharges != null) lblBottomCharges.setText(String.format("₹ %.2f", charges));
        if (lblBottomNet != null) lblBottomNet.setText(String.format("₹ %.2f", total));
        double taxableCharges = invoiceCharges.stream().filter(SalesCharge::isTaxable).mapToDouble(SalesCharge::getAmount).sum();
        if (lblTaxableAmount != null) lblTaxableAmount.setText(String.format("₹ %.2f", net + taxableCharges));
        if (lblCharges != null) lblCharges.setText(String.format("₹ %.2f", charges));
        if (lblChargeCaption != null) {
            lblChargeCaption.setText(invoiceCharges.isEmpty()?"Additional Charges":"Additional Charges • "+invoiceCharges.size());
        }

    }

    @FXML
    private void manageCharges() {
        List<SalesCharge> draft = invoiceCharges.stream().map(SalesCharge::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Dialog<ButtonType> dialog = new OwnedDialog<>();
        dialog.setTitle("Additional Charges");
        dialog.setHeaderText("Add up to two additional invoice charges");
        dialog.getDialogPane().getStyleClass().add("sales-charge-dialog");

        VBox rows = new VBox(9);
        rows.getStyleClass().add("sales-charge-editor-rows");
        Label totals = new Label();
        totals.getStyleClass().add("sales-charge-editor-total");
        Button add = new Button("Add Charge", IconFactory.compactIcon("add", 14));
        add.getStyleClass().addAll("approved-button", "approved-primary-button", "sales-charge-add");
        Label limit = new Label("Maximum: 2 charges");
        limit.getStyleClass().add("sales-charge-limit");
        HBox addBar = new HBox(10, add, new Region(), limit);
        HBox.setHgrow(addBar.getChildren().get(1), Priority.ALWAYS);
        addBar.setAlignment(Pos.CENTER_LEFT);

        Runnable updateTotals = () -> {
            double beforeTax = draft.stream().mapToDouble(SalesCharge::getAmount).sum();
            double tax = draft.stream().mapToDouble(SalesCharge::getTaxAmount).sum();
            totals.setText(String.format("Charges ₹ %,.2f    GST ₹ %,.2f    Total ₹ %,.2f", beforeTax, tax, beforeTax + tax));
            add.setDisable(draft.size() >= 2);
        };
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            rows.getChildren().clear();
            for (int index = 0; index < draft.size(); index++) {
                SalesCharge charge = draft.get(index);
                ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(availableChargeTypes));
                if (!charge.getChargeType().isBlank() && !type.getItems().contains(charge.getChargeType())) type.getItems().add(charge.getChargeType());
                type.setValue(charge.getChargeType().isBlank() ? null : charge.getChargeType());
                type.setPromptText("Select charge..."); type.setMaxWidth(Double.MAX_VALUE);
                TextField amount = new TextField(charge.getAmount() <= 0 ? "" : String.format(java.util.Locale.ROOT, "%.2f", charge.getAmount()));
                amount.setPromptText("Amount");
                ComboBox<String> tax = new ComboBox<>(FXCollections.observableArrayList("Non-taxable", "Taxable 0%", "Taxable 5%", "Taxable 12%", "Taxable 18%", "Taxable 28%"));
                tax.setValue(charge.isTaxable() ? "Taxable " + percentText(charge.getGstPercent()) : "Non-taxable");
                Button remove = new Button("Remove", IconFactory.compactIcon("delete", 13));
                remove.getStyleClass().addAll("approved-button", "approved-danger-button", "sales-charge-remove");
                int rowIndex = index;
                type.valueProperty().addListener((o,a,b)->charge.setChargeType(b));
                amount.textProperty().addListener((o,a,b)->{charge.setAmount(parseAmount(b));updateTotals.run();});
                tax.valueProperty().addListener((o,a,b)->{applyTaxTreatment(charge,b);updateTotals.run();});
                remove.setOnAction(e->{draft.remove(rowIndex);render[0].run();});
                GridPane row = new GridPane(); row.setHgap(8); row.setVgap(3);
                row.getStyleClass().add("sales-charge-editor-row");
                row.add(new Label("Charge " + (index + 1)),0,0);
                row.add(new Label("Amount"),1,0);
                row.add(new Label("Tax Treatment"),2,0);
                row.add(type,0,1); row.add(amount,1,1); row.add(tax,2,1); row.add(remove,3,1);
                GridPane.setHgrow(type,Priority.ALWAYS);
                rows.getChildren().add(row);
            }
            if (draft.isEmpty()) {
                Label empty = new Label("No additional charges. Select Add Charge when required.");
                empty.getStyleClass().add("sales-charge-editor-empty"); rows.getChildren().add(empty);
            }
            updateTotals.run();
        };
        add.setOnAction(e->{if(draft.size()<2){draft.add(new SalesCharge("",0,true,18));render[0].run();}});
        render[0].run();

        ScrollPane rowScroller = new ScrollPane(rows);
        rowScroller.setFitToWidth(true);
        rowScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rowScroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rowScroller.setPannable(true);
        rowScroller.setPrefViewportHeight(175);
        rowScroller.setMinHeight(120);
        rowScroller.setMaxHeight(210);
        rowScroller.getStyleClass().add("sales-charge-editor-scroll");

        VBox content = new VBox(12, rowScroller, addBar, new Separator(), totals);
        content.setPrefWidth(700);
        content.setMinHeight(260);
        dialog.getDialogPane().setMinSize(680, 440);
        dialog.getDialogPane().setPrefSize(740, 480);
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(content);
        ButtonType apply = new ButtonType("Apply Charges", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, apply);
        Node applyButton = dialog.getDialogPane().lookupButton(apply);
        applyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validateCharges(draft);
            if (error != null) { event.consume(); warn(error); }
        });
        dialog.showAndWait().filter(apply::equals).ifPresent(result -> invoiceCharges.setAll(draft.stream().map(SalesCharge::copy).toList()));
    }

    private void updateChargeManagerSummary() {
        if (lblChargeManagerSummary == null) return;
        double amount = invoiceCharges.stream().mapToDouble(SalesCharge::getAmount).sum();
        lblChargeManagerSummary.setText(invoiceCharges.isEmpty() ? "No additional charges"
                : String.format("%d charge%s · ₹ %,.2f", invoiceCharges.size(), invoiceCharges.size()==1?"":"s", amount));
    }

    private String validateCharges(List<SalesCharge> charges) {
        if (charges == null || charges.isEmpty()) return null;
        if (charges.size() > 2) return "A maximum of two additional charges is allowed.";
        java.util.Set<String> names = new java.util.HashSet<>();
        for (SalesCharge charge : charges) {
            if (charge == null || charge.getChargeType().isBlank()) return "Select a charge type for every charge row.";
            if (charge.getAmount() <= 0) return "Charge amount must be greater than zero.";
            if (!names.add(normalized(charge.getChargeType()))) return "The same charge type cannot be selected twice.";
        }
        return null;
    }

    private void applyTaxTreatment(SalesCharge charge, String treatment) {
        if (treatment == null || treatment.startsWith("Non")) { charge.setTaxable(false); charge.setGstPercent(0); return; }
        charge.setTaxable(true);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([0-9.]+)").matcher(treatment);
        charge.setGstPercent(matcher.find() ? Double.parseDouble(matcher.group(1)) : 0);
    }

    private String percentText(double percent) { return String.format(java.util.Locale.ROOT, percent % 1 == 0 ? "%.0f%%" : "%.2f%%", percent); }
    private double parseAmount(String value) { try { return value==null||value.isBlank()?0:Double.parseDouble(value.replace(",","")); } catch(Exception e) { return 0; } }

    private double number(TextField field){try{return field==null||field.getText()==null||field.getText().isBlank()?0:Double.parseDouble(field.getText().replace(",",""));}catch(Exception e){return 0;}}

    @FXML private void addMultipleItems(){new OwnedAlert(Alert.AlertType.INFORMATION,"Select an item, enter quantity/rate/tax and click Add Item. Repeat for each required item.").showAndWait();}
    @FXML private void scanBarcode(){TextInputDialog d=new OwnedTextInputDialog();d.setHeaderText("Scan or enter item code");d.showAndWait().ifPresent(code->{String value=code.trim();if(value.isBlank())return;UiTaskExecutor.submitLatest("create-sale-barcode-search",()->itemService.search(value,12),matches->matches.stream().filter(i->safeItem(i.getItemCode()).equalsIgnoreCase(value)).findFirst().ifPresentOrElse(i->{mergeItemCache(List.of(i));selectItem(i);},()->warn("Item code not found")),error->warn("Item search failed: "+rootMessage(error)));});}
    @FXML private void attachFile(){javafx.stage.FileChooser c=new javafx.stage.FileChooser();java.io.File f=c.showOpenDialog(tableLines.getScene().getWindow());if(f!=null)txtAttachment.setText(f.getAbsolutePath());}
    @FXML private void preview(){Sales sale=buildSale();if(sale!=null)new OwnedAlert(Alert.AlertType.INFORMATION,"Invoice "+sale.getInvoiceNo()+"\nCustomer: "+sale.getCustomer().getName()+"\nItems: "+sale.getLines().size()+"\nTotal: "+String.format("₹ %,.2f",sale.getTotalAmount())).showAndWait();}
    @FXML private void saveDraft(){Sales sale=buildSale();if(sale==null)return;sale.setRemarks("DRAFT\n"+sale.getRemarks());try{salesService.save(sale);NotificationService.add("Draft sales invoice "+sale.getInvoiceNo()+" saved.");cancel();}catch(Exception e){warn(e.getMessage());}}


//--------------------------------------------------
// WARNING
//--------------------------------------------------

    private void warn(String msg) {

        new OwnedAlert(
            Alert.AlertType.WARNING,
            msg
        ).showAndWait();

    }


//--------------------------------------------------
// CANCEL
//--------------------------------------------------

    @FXML
    private void cancel() {

        NavigationManager.getInstance()
            .loadPage("/fxml/pages/SalesList.fxml");

    }


//--------------------------------------------------
// LOAD SALE FOR EDIT
//--------------------------------------------------

    public void loadSale(Sales sale) {

        System.out.println(
            "Invoice = " + sale.getInvoiceNo()
        );

        editingSale = sale;
        loadingSaleForEdit = true;

        // Capture persisted delivery state before customer selection fires its
        // listener. During edit, the customer listener must not replace a saved
        // independent delivery address/GSTIN with billing details.
        final boolean savedSameAsBilling = sale.isSameAsBilling();
        final String savedDeliveryAddress = sale.getDeliveryAddress();
        final String savedDeliveryGstin = sale.getDeliveryGstin();
        if (chkSameAsBilling != null) chkSameAsBilling.setSelected(false);

        txtInvoiceNo.setText(
            sale.getInvoiceNo()
        );

        lblInvoiceDisplay.setText(
            sale.getInvoiceNo()
        );

        dpInvoiceDate.setValue(
            sale.getInvoiceDate()
        );
        if (txtPoDate != null) txtPoDate.setText(BusinessClock.formatDate(sale.getPoDate()));
        cmbSalesPerson.setValue(sale.getSalesperson().isBlank()?"Admin":sale.getSalesperson());
        txtInvoiceMessage.setText(sale.getNotes());
        cmbPaymentTerms.setValue(sale.getPaymentTerms().isBlank() ? "15 Days" : sale.getPaymentTerms());
        if (cmbGstType != null) cmbGstType.setValue(sale.getGstType().isBlank()
            ? (cmbGstType.getItems().isEmpty() ? null : cmbGstType.getItems().get(0)) : sale.getGstType());
        if (cmbTransporter != null) cmbTransporter.getItems().stream()
            .filter(value -> value.getLookupValue().equalsIgnoreCase(sale.getTransporter()))
            .findFirst().ifPresent(value -> cmbTransporter.setValue(value));
        if (txtVehicleNumber != null) txtVehicleNumber.setText(sale.getVehicleNumber());
        if (txtContactPerson != null) txtContactPerson.setText(sale.getContactPerson());
        if (txtContactPersonMobile != null) txtContactPersonMobile.setText(sale.getContactPersonMobile());
        invoiceCharges.setAll(sale.getCharges().stream().map(SalesCharge::copy).toList());
        if (txtTransportNote != null) txtTransportNote.setText(sale.getTransportNote());
        if (txtOrderNo != null) {
            String savedCustomerPo = sale.getOrderNo();
            // Defensive compatibility for databases created by older 7.1.x
            // builds. The server migration clears these values too, but the UI
            // must never present the obsolete internal sequence as customer PO.
            txtOrderNo.setText(isLegacyGeneratedPoOrderNo(savedCustomerPo) ? "" : savedCustomerPo);
        }
        String customerGstin = sale.getCustomer() == null ? "" : sale.getCustomer().getGstin();
        if (txtBillingGstin != null) txtBillingGstin.setText(!sale.getBillingGstin().isBlank()
            ? sale.getBillingGstin() : (!sale.getGstin().isBlank() ? sale.getGstin() : customerGstin));
        if (txtTransporterGstin != null) txtTransporterGstin.setText(sale.getTransporterGstin());
        txtReference.setText(sale.getReferenceNo() == null ? "" : sale.getReferenceNo());

        // Select customer

        if (sale.getCustomer() != null) {

            txtBillingAddress.setText(sale.getCustomer().getAddress() == null
                ? "" : sale.getCustomer().getAddress());

            for (Party party : cmbCustomer.getItems()) {

                if (party.getId()
                    == sale.getCustomer().getId()) {

                    cmbCustomer.getSelectionModel()
                        .select(party);

                    break;

                }

            }

        }

        if (txtBillingAddress != null) {
            String billing = sale.getBillingAddress().isBlank()
                ? (sale.getCustomer() == null ? "" : sale.getCustomer().getAddress())
                : sale.getBillingAddress();
            txtBillingAddress.setText(billing == null ? "" : billing);
        }
        if (chkSameAsBilling != null) {
            chkSameAsBilling.setSelected(savedSameAsBilling);
        }
        if (savedSameAsBilling) {
            syncDeliveryAddressState();
        } else {
            if (txtDeliveryAddress != null) txtDeliveryAddress.setText(savedDeliveryAddress == null ? "" : savedDeliveryAddress);
            if (txtDeliveryGstin != null) txtDeliveryGstin.setText(savedDeliveryGstin == null ? "" : savedDeliveryGstin);
            syncDeliveryAddressState();
        }

        txtRemarks.setText(

            sale.getRemarks() == null

                ? ""

                : sale.getRemarks()

        );

        tableLines.getItems().clear();

        if (sale.getLines() != null) {

            tableLines.getItems()
                .addAll(
                    sale.getLines()
                );

        }

        recalculate();
        loadingSaleForEdit = false;

    }


//--------------------------------------------------
// VIEW MODE
//--------------------------------------------------

    public void setViewMode(boolean value) {

        txtInvoiceNo.setDisable(value);

        dpInvoiceDate.setDisable(value);

        cmbCustomer.setDisable(value);

        txtItemSearch.setDisable(value);
        if (txtPoDate != null) txtPoDate.setDisable(value);

        txtQuantity.setDisable(value);

        txtRate.setDisable(value);

        txtGST.setDisable(value);
        txtLineDiscount.setDisable(value);

        txtRemarks.setDisable(value);
        txtBillingAddress.setDisable(value);
        txtDeliveryAddress.setDisable(value);
        if (cmbGstType != null) cmbGstType.setDisable(value);
        if (cmbTransporter != null) cmbTransporter.setDisable(value);
        if (txtVehicleNumber != null) txtVehicleNumber.setDisable(value);
        if (txtContactPerson != null) txtContactPerson.setDisable(value);
        if (txtContactPersonMobile != null) txtContactPersonMobile.setDisable(value);
        if (btnManageCharges != null) btnManageCharges.setDisable(value);
        if (txtTransportNote != null) txtTransportNote.setDisable(value);
        if (txtOrderNo != null) txtOrderNo.setDisable(value);
        if (txtBillingGstin != null) txtBillingGstin.setDisable(value);
        if (txtDeliveryGstin != null) txtDeliveryGstin.setDisable(value);
        if (txtTransporterGstin != null) txtTransporterGstin.setDisable(value);
        if (chkSameAsBilling != null) chkSameAsBilling.setDisable(value);

        btnAddLine.setDisable(value);
        if (btnRemoveLine != null) btnRemoveLine.setDisable(value);
        if (btnSaveDraft != null) btnSaveDraft.setDisable(value);
        if (btnAddCustomer != null) btnAddCustomer.setDisable(value);

        btnSaveSale.setDisable(value);

        tableLines.setDisable(value);

    }
    @FXML
    private void addLine(){


        Item item = resolveTypedItem(txtItemSearch.getText());
        if (item != null && selectedItem == null) selectItem(item);


        if(item==null){

            warn("Select item");

            return;
        }



        try{


            double qty =
                Double.parseDouble(txtQuantity.getText());


            double rate =
                Double.parseDouble(txtRate.getText());

            if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
            if (rate < 0) throw new IllegalArgumentException("Rate cannot be negative");
            double alreadyOnInvoice = tableLines.getItems().stream()
                .filter(line -> line != editingLine && item.getItemCode().equals(line.getItemCode()))
                .mapToDouble(SalesLine::getQuantity).sum();
            if (qty + alreadyOnInvoice > item.getOpeningStock()) {
                throw new IllegalArgumentException("Only " + item.getOpeningStock() + " units of " + item.getDescription() + " are available in stock");
            }


            double gst =
                item.getGst();
            double discount = item.getDiscountPercent();



            if(txtGST.getText()!=null &&
                !txtGST.getText().isBlank()){

                gst =
                    Double.parseDouble(txtGST.getText());

            }



            if (txtLineDiscount.getText() != null && !txtLineDiscount.getText().isBlank()) {
                discount = Double.parseDouble(txtLineDiscount.getText());
            }
            if (discount < 0 || discount > 100) throw new IllegalArgumentException("Discount must be between 0 and 100");



            SalesLine line =
                new SalesLine();


            line.setItemCode(
                item.getItemCode()
            );


            line.setItemDescription(itemRemark(item));


            line.setQuantity(qty);


            line.setRate(rate);


            line.setGstPercent(gst);
            line.setDiscountPercent(discount);
            line.recalculate();



            if(editingLine == null){

                tableLines.getItems().add(line);

            }else{

                tableLines.getItems().set(editingIndex, line);

                editingLine = null;
                editingIndex = -1;

            }



            clearItemSearch();

            txtQuantity.clear();

            txtRate.clear();

            txtGST.clear();
            txtLineDiscount.clear();
            tableLines.getSelectionModel().clearSelection();


            recalculate();



        }
        catch(Exception e){
            warn(e instanceof NumberFormatException ? "Enter valid quantity and rate" : e.getMessage());

        }

    }

    private void configureEmptyState() {
        VBox placeholder = new VBox(8);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.getStyleClass().add("sales-entry-empty-state");

        Label icon = new Label();
        icon.setGraphic(IconFactory.icon("item", 34));
        icon.getStyleClass().add("sales-entry-empty-icon");

        Label title = new Label("No items added yet");
        title.getStyleClass().add("sales-entry-empty-title");

        Label message = new Label("Use the controls above to add invoice items");
        message.getStyleClass().add("sales-entry-empty-message");

        placeholder.getChildren().addAll(icon, title, message);
        tableLines.setPlaceholder(placeholder);
    }

    private void decorateActions() {
        if (salesEntryRoot == null) return;

        Node titleHolder = salesEntryRoot.lookup(".sales-entry-title-icon");
        if (titleHolder instanceof StackPane stackPane) {
            stackPane.getChildren().setAll(IconFactory.icon("sale", 24));
        }

        for (Node node : salesEntryRoot.lookupAll(".button")) {
            if (!(node instanceof Button button) || button.getGraphic() != null) continue;

            String text = button.getText() == null ? "" : button.getText().trim().toLowerCase();
            String key = text.contains("back") ? "return" :
                text.contains("preview") ? "view" :
                text.contains("pdf") ? "download" :
                text.contains("email") ? "email" :
                text.contains("whatsapp") ? "whatsapp" :
                text.contains("remove") ? "delete" :
                text.contains("cancel") ? "cancel" :
                text.contains("draft") ? "save" :
                text.contains("save") ? "print" :
                text.contains("add customer") ? "customer" :
                text.contains("add") ? "add" : null;

            if (key != null) {
                button.setGraphic(IconFactory.icon(key));
                button.getProperties().put("erp-icon-explicit", true);
            }
        }
    }

    @FXML
    private void cancelEdit() {

        editingLine = null;
        editingIndex = -1;

        clearItemSearch();

        txtQuantity.clear();
        txtRate.clear();
        txtGST.clear();

        tableLines.getSelectionModel().clearSelection();

        btnAddLine.setText("+ Add Line");
    }



    @FXML
    private void removeLine(){

        SalesLine line =
            tableLines
                .getSelectionModel()
                .getSelectedItem();


        if(line!=null){

            tableLines.getItems().remove(line);

            recalculate();

        }

    }




    private void safeLoad(String label, List<String> errors, Runnable loader) {
        try {
            loader.run();
        } catch (Exception ex) {
            String message = rootMessage(ex);
            errors.add(label + ": " + message);
            System.err.println("Create Sale initialization - " + label + ": " + message);
            Platform.runLater(() -> {
                if (errors.size() == 1) {
                    new OwnedAlert(Alert.AlertType.WARNING,
                        "Create Sale opened, but some API-backed master data could not be loaded.\n\n" +
                        String.join("\n", errors) +
                        "\n\nCheck that the Spring server is running and review its console for the matching endpoint error.")
                        .showAndWait();
                }
            });
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private void configureExplicitTableHeaderIcons() {
        IconFactory.applyTableHeaderIcon(colItem, "item");
        IconFactory.applyTableHeaderIcon(colQuantity, "quantity");
        IconFactory.applyTableHeaderIcon(colRate, "currency");
        IconFactory.applyTableHeaderIcon(colGst, "tax");
        IconFactory.applyTableHeaderIcon(colDiscount, "discount");
        IconFactory.applyTableHeaderIcon(colDiscountAmount, "discount");
        IconFactory.applyTableHeaderIcon(colGstAmount, "tax");
        IconFactory.applyTableHeaderIcon(colNetAmount, "currency");
        IconFactory.applyTableHeaderIcon(colTotal, "currency");
    }
}
