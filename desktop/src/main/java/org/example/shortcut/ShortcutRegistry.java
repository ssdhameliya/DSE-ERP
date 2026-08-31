package org.example.shortcut;

import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ButtonBase;
import javafx.application.Platform;
import java.lang.ref.WeakReference;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.example.config.ConfigManager;
import org.example.navigation.NavigationManager;
import org.example.service.PermissionService;
import org.example.service.SessionService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Central persisted keyboard shortcut registry and action catalog. */
public final class ShortcutRegistry {
    public enum Scope {
        GLOBAL("Application (Global)"),
        CURRENT_SCREEN("Current Screen Context"),
        SALES("Sales"), PURCHASE("Purchase"), INVENTORY("Inventory & Items"),
        CUSTOMERS("Customers"), SUPPLIERS("Suppliers"), REPORTS("Reports"),
        COMMUNICATION("Communication"), SETTINGS("Settings"),
        PDF_STUDIO("PDF Studio"), EXCEL_STUDIO("Excel Studio"), MASTER_DATA("Master Data");

        private final String label;
        Scope(String label){this.label=label;}
        public String label(){return label;}
        public static Scope fromStored(String value, Scope fallback){
            if(value==null||value.isBlank())return fallback;
            for(Scope scope:values()) if(scope.name().equalsIgnoreCase(value)||scope.label.equalsIgnoreCase(value.trim())) return scope;
            return fallback;
        }
    }

    /**
     * Complete user-facing shortcut action catalog. New navigation destinations default to Disabled,
     * so users can assign them without a future Java change to Settings.
     */
    public enum Action {
        GLOBAL_SEARCH("global.search", "Global Search", "Search & Filter", "Shortcut+K", Scope.GLOBAL, null),
        TOGGLE_SIDEBAR("global.toggleSidebar", "Show / Hide Sidebar", "Application Actions", "Shortcut+B", Scope.GLOBAL, null),
        SAVE_CURRENT("global.saveCurrent", "Save Current", "Application Actions", "Shortcut+S", Scope.GLOBAL, null),
        EDIT_CURRENT("global.editCurrent", "Edit Current / Selected", "Application Actions", "Shortcut+E", Scope.GLOBAL, null),
        REFRESH_CURRENT("global.refreshCurrent", "Refresh Current Page", "Application Actions", "F5", Scope.GLOBAL, null),
        NEW_CURRENT("global.newCurrent", "New in Current Page", "Application Actions", "Shortcut+N", Scope.GLOBAL, null),
        OPEN_SELECTED("global.openSelected", "Open Selected", "Application Actions", "ENTER", Scope.GLOBAL, null),
        DELETE_SELECTED("global.deleteSelected", "Delete Selected", "Application Actions", "DELETE", Scope.GLOBAL, null),
        PRINT_CURRENT("global.printCurrent", "Print Current", "Application Actions", "Shortcut+P", Scope.GLOBAL, null),
        EXPORT_CURRENT("global.exportCurrent", "Export Current", "Application Actions", "Shortcut+Shift+E", Scope.GLOBAL, null),
        CLOSE_BACK("global.closeBack", "Close / Back", "Application Actions", "ESC", Scope.GLOBAL, null),

        NEW_SALE("nav.newSale", "New Sale", "Quick Create", "F9", Scope.GLOBAL, "SALES.VIEW"),
        NEW_PURCHASE("nav.newPurchase", "New Purchase", "Quick Create", "", Scope.GLOBAL, "PURCHASE.VIEW"),
        NEW_QUOTATION("nav.newQuotation", "New Quotation", "Quick Create", "F3", Scope.GLOBAL, "QUOTATION.VIEW"),
        NEW_CUSTOMER("nav.newCustomer", "New Customer", "Quick Create", "", Scope.GLOBAL, "CUSTOMERS.VIEW"),
        NEW_SUPPLIER("nav.newSupplier", "New Supplier", "Quick Create", "", Scope.GLOBAL, "SUPPLIERS.VIEW"),
        NEW_MASTER("nav.newMaster", "New Master Entry", "Quick Create", "", Scope.GLOBAL, "MASTERS.VIEW"),

        DASHBOARD("nav.dashboard", "Dashboard", "Navigation", "", Scope.GLOBAL, null),
        SALES_REGISTER("nav.salesRegister", "Sales Register", "Navigation", "", Scope.GLOBAL, "SALES.VIEW"),
        SALES_RETURN("nav.salesReturn", "Sales Return Register", "Navigation", "", Scope.GLOBAL, "SALES.VIEW"),
        QUOTATION_REGISTER("nav.quotationRegister", "Quotation Register", "Navigation", "", Scope.GLOBAL, "QUOTATION.VIEW"),
        PURCHASE_REGISTER("nav.purchaseRegister", "Purchase Register", "Navigation", "", Scope.GLOBAL, "PURCHASE.VIEW"),
        PURCHASE_RETURN("nav.purchaseReturn", "Purchase Return Register", "Navigation", "", Scope.GLOBAL, "PURCHASE.VIEW"),
        ITEM_MASTER("nav.itemMaster", "Item Master", "Navigation", "F4", Scope.GLOBAL, "INVENTORY.VIEW"),
        INVENTORY("nav.inventory", "Inventory", "Navigation", "", Scope.GLOBAL, "INVENTORY.VIEW"),
        CUSTOMERS("nav.customers", "Customers", "Navigation", "", Scope.GLOBAL, "CUSTOMERS.VIEW"),
        SUPPLIERS("nav.suppliers", "Suppliers", "Navigation", "", Scope.GLOBAL, "SUPPLIERS.VIEW"),
        MASTERS("nav.masters", "Master Data", "Navigation", "F10", Scope.GLOBAL, "MASTERS.VIEW"),
        BANK_STATEMENT("nav.bankStatement", "Bank Statement", "Navigation", "F6", Scope.GLOBAL, "BANK_EXPENSE.VIEW"),
        BANK_ENTRY("nav.bankEntry", "Bank Entry", "Navigation", "F7", Scope.GLOBAL, "BANK_EXPENSE.VIEW"),
        EXPENSE_ENTRY("nav.expenseEntry", "Expense Entry", "Navigation", "F8", Scope.GLOBAL, "BANK_EXPENSE.VIEW"),
        REMINDERS("nav.reminders", "Reminder Center", "Navigation", "", Scope.GLOBAL, "REMINDERS.VIEW"),
        USER_ACCESS("nav.userAccess", "User Access & Permissions", "Navigation", "", Scope.GLOBAL, "USERS.VIEW"),
        NOTIFICATION_CENTER("nav.notificationCenter", "Notification Center", "Navigation", "", Scope.GLOBAL, null),

        MY_PROFILE("session.profile", "My Profile", "Session & Help", "", Scope.GLOBAL, null),
        CHANGE_PASSWORD("session.changePassword", "Change Password", "Session & Help", "", Scope.GLOBAL, null),
        TOGGLE_THEME("session.toggleTheme", "Toggle Light / Dark Theme", "Session & Help", "", Scope.GLOBAL, null),
        SHORTCUT_HELP("session.shortcutHelp", "Shortcut Help", "Session & Help", "", Scope.GLOBAL, null),
        LOGOUT("session.logout", "Logout", "Session & Help", "", Scope.GLOBAL, null),

        REPORTS("nav.reports", "Reports", "Reports & Tools", "", Scope.GLOBAL, "REPORTS.VIEW"),
        DATA_IMPORT("nav.dataImport", "Data Import", "Reports & Tools", "", Scope.GLOBAL, "IMPORT.VIEW"),
        COMMUNICATION("nav.communication", "Communication Center", "Reports & Tools", "", Scope.GLOBAL, null),
        EMAIL_CENTER("nav.emailCenter", "Email Center", "Reports & Tools", "", Scope.GLOBAL, null),
        WHATSAPP_CENTER("nav.whatsappCenter", "WhatsApp Activity", "Reports & Tools", "", Scope.GLOBAL, null),
        PDF_STUDIO_OPEN("nav.pdfStudio", "PDF Studio", "Reports & Tools", "", Scope.GLOBAL, "DOCUMENT_STUDIO.VIEW"),
        EXCEL_STUDIO_OPEN("nav.excelStudio", "Excel Studio", "Reports & Tools", "", Scope.GLOBAL, "DOCUMENT_STUDIO.VIEW"),
        BACKUP_RESTORE("nav.backup", "Backup & Restore", "Reports & Tools", "", Scope.GLOBAL, "BACKUP.VIEW"),
        SAFE_ROLLBACK("nav.safeRollback", "Safe Rollback", "Reports & Tools", "", Scope.GLOBAL, "SAFE_ROLLBACK.VIEW"),

        SETTINGS_COMPANY("settings.company", "Company & Billing", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),
        SETTINGS_PAYMENT("settings.payment", "Payment & Bank Settings", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),
        SETTINGS_INVOICE("settings.invoice", "Invoice & Delivery Settings", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),
        SETTINGS_NOTIFICATIONS("settings.notifications", "Notification Settings", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),
        SETTINGS_EMAIL("settings.email", "Email Settings", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),
        SETTINGS_WORKSPACE("settings.workspace", "Workspace & Storage", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),
        SETTINGS_SHORTCUTS("settings.shortcuts", "Keyboard Shortcuts", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),
        SETTINGS_UPDATES("settings.updates", "Application Updates", "Settings & Tools", "", Scope.GLOBAL, "SETTINGS.VIEW"),

        PDF_UNDO("pdf.undo", "Undo", "PDF Studio", "Shortcut+Z", Scope.PDF_STUDIO, null),
        PDF_REDO("pdf.redo", "Redo", "PDF Studio", "Shortcut+Y", Scope.PDF_STUDIO, null),
        PDF_DUPLICATE("pdf.duplicate", "Duplicate Selected", "PDF Studio", "Shortcut+D", Scope.PDF_STUDIO, null),
        PDF_DELETE("pdf.delete", "Delete Selected", "PDF Studio", "DELETE", Scope.PDF_STUDIO, null),

        EXCEL_UNDO("excel.undo", "Undo", "Excel Studio", "Shortcut+Z", Scope.EXCEL_STUDIO, null),
        EXCEL_REDO("excel.redo", "Redo", "Excel Studio", "Shortcut+Y", Scope.EXCEL_STUDIO, null),
        EXCEL_REDO_ALT("excel.redoAlt", "Redo (Alternate)", "Excel Studio", "Shortcut+Shift+Z", Scope.EXCEL_STUDIO, null),
        EXCEL_COPY("excel.copy", "Copy Cell", "Excel Studio", "Shortcut+C", Scope.EXCEL_STUDIO, null),
        EXCEL_PASTE("excel.paste", "Paste Cell", "Excel Studio", "Shortcut+V", Scope.EXCEL_STUDIO, null),
        EXCEL_EDIT("excel.edit", "Edit Active Cell", "Excel Studio", "F2", Scope.EXCEL_STUDIO, null),
        EXCEL_CLEAR("excel.clear", "Clear Selected Cell / Range", "Excel Studio", "DELETE", Scope.EXCEL_STUDIO, null),

        MASTER_DELETE("master.delete", "Delete Selected Master", "Master Data", "DELETE", Scope.MASTER_DATA, "MASTERS.VIEW"),
        MASTER_EDIT("master.edit", "Edit Selected Master", "Master Data", "ENTER", Scope.MASTER_DATA, "MASTERS.VIEW"),
        MASTER_REFRESH("master.refresh", "Refresh Master Data", "Master Data", "F5", Scope.MASTER_DATA, "MASTERS.VIEW"),
        MASTER_NEW("master.new", "New Master Entry", "Master Data", "Shortcut+N", Scope.MASTER_DATA, "MASTERS.VIEW");

        private final String id, label, category, defaultBinding, permission;
        private final Scope scope;
        Action(String id,String label,String category,String defaultBinding,Scope scope,String permission){
            this.id=id;this.label=label;this.category=category;this.defaultBinding=defaultBinding;this.scope=scope;this.permission=permission;
        }
        public String id(){return id;} public String label(){return label;} public String category(){return category;}
        public String defaultBinding(){return defaultBinding;} public Scope scope(){return scope;} public String permission(){return permission;}
    }

    private static final String CONFIG_PREFIX="shortcut.";
    private static final EnumMap<Action, CopyOnWriteArrayList<WeakReference<ButtonBase>>> LABEL_BINDINGS = new EnumMap<>(Action.class);
    private ShortcutRegistry(){}

    /**
     * Central live action-label binding. FXML stores only the base label; the currently
     * configured user shortcut is appended at runtime and refreshed after every save.
     * Example: New Sale + F2 -> "New Sale F2"; changing to F9 updates every bound button.
     */
    public static void bindLabel(ButtonBase button, Action action) {
        bindLabel(button, action, button == null ? null : button.getText());
    }

    public static void bindLabel(ButtonBase button, Action action, String baseLabel) {
        if (button == null || action == null) return;
        String base = baseLabel == null || baseLabel.isBlank() ? action.label() : baseLabel.trim();
        button.getProperties().put("erp.shortcut.action", action);
        button.getProperties().put("erp.shortcut.baseLabel", base);
        CopyOnWriteArrayList<WeakReference<ButtonBase>> refs = LABEL_BINDINGS.computeIfAbsent(action, ignored -> new CopyOnWriteArrayList<>());
        boolean alreadyBound = refs.stream().map(WeakReference::get).anyMatch(existing -> existing == button);
        if (!alreadyBound) refs.add(new WeakReference<>(button));
        refreshBoundLabel(button, action, base);
    }

    public static String displayLabel(Action action, String baseLabel) {
        String base = baseLabel == null || baseLabel.isBlank() ? (action == null ? "" : action.label()) : baseLabel.trim();
        if (action == null) return base;
        String key = display(action);
        return key == null || key.isBlank() || "Disabled".equalsIgnoreCase(key) ? base : base + " " + key;
    }

    public static void refreshBoundLabels() {
        Runnable update = () -> {
            for (var entry : LABEL_BINDINGS.entrySet()) {
                Action action = entry.getKey();
                entry.getValue().removeIf(ref -> {
                    ButtonBase button = ref.get();
                    if (button == null) return true;
                    Object base = button.getProperties().get("erp.shortcut.baseLabel");
                    refreshBoundLabel(button, action, base instanceof String text ? text : action.label());
                    return false;
                });
            }
        };
        if (Platform.isFxApplicationThread()) update.run(); else Platform.runLater(update);
    }

    private static void refreshBoundLabel(ButtonBase button, Action action, String baseLabel) {
        button.setText(displayLabel(action, baseLabel));
        String accessible = displayLabel(action, baseLabel);
        button.setAccessibleText(accessible);
        if (button.getTooltip() != null && button.getTooltip().getText() != null
                && button.getTooltip().getText().startsWith(baseLabel)) {
            button.getTooltip().setText(accessible);
        }
    }

    public static List<Action> actions(){return List.of(Action.values());}
    public static List<Action> availableActions(){return actions().stream().filter(ShortcutRegistry::permitted).toList();}
    public static List<Action> actions(Scope scope){return actions().stream().filter(a->a.scope()==scope).toList();}

    public static String configuredBinding(Action action){
        if(action==null)return ""; String legacy=ConfigManager.get(CONFIG_PREFIX+action.id(),action.defaultBinding());
        return normalize(ConfigManager.get(storageKey(action),legacy));
    }
    public static Scope configuredScope(Action action){
        if(action==null)return Scope.GLOBAL;
        return Scope.fromStored(ConfigManager.get(optionKey(action,"scope"),action.scope().name()),action.scope());
    }
    public static boolean allowInTextInput(Action action){
        return boolOption(action,"allowText",action==Action.TOGGLE_SIDEBAR);
    }
    public static boolean requireSelection(Action action){
        boolean fallback=action==Action.EDIT_CURRENT||action==Action.OPEN_SELECTED||action==Action.DELETE_SELECTED;
        return boolOption(action,"requireSelection",fallback);
    }
    private static boolean boolOption(Action action,String suffix,boolean fallback){
        return Boolean.parseBoolean(ConfigManager.get(optionKey(action,suffix),Boolean.toString(fallback)));
    }
    public static void saveOptions(Action action,Scope scope,boolean allowText,boolean requireSelection){
        if(action==null)return;
        ConfigManager.setWithoutSaving(optionKey(action,"scope"),(scope==null?action.scope():scope).name());
        ConfigManager.setWithoutSaving(optionKey(action,"allowText"),Boolean.toString(allowText));
        ConfigManager.setWithoutSaving(optionKey(action,"requireSelection"),Boolean.toString(requireSelection));
        ConfigManager.save();
        refreshBoundLabels();
    }

    public static String storageKey(Action action){
        if(action==null)return CONFIG_PREFIX+"unknown"; var user=SessionService.current(); String username=user==null?"":user.getUsername();
        if(username==null||username.isBlank())return CONFIG_PREFIX+action.id();
        String safe=username.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]","_");
        return CONFIG_PREFIX+"user."+safe+"."+action.id();
    }
    private static String optionKey(Action action,String suffix){return storageKey(action)+"."+suffix;}
    public static String defaultBinding(Action action){return action==null?"":action.defaultBinding();}
    public static KeyCombination combination(Action action){
        String configured=configuredBinding(action); if(configured.isBlank())return null;
        try{return KeyCombination.valueOf(configured);}catch(Exception ignored){try{return KeyCombination.valueOf(action.defaultBinding());}catch(Exception impossible){return null;}}
    }
    public static boolean matches(KeyEvent event,Action action){KeyCombination c=combination(action);return event!=null&&c!=null&&c.match(event);}
    public static boolean permitted(Action action){return action!=null&&(action.permission()==null||action.permission().isBlank()||PermissionService.allowed(action.permission()));}
    public static String display(Action action){String raw=configuredBinding(action);return raw.isBlank()?"Disabled":displayRaw(raw);}

    public static String fromEvent(KeyEvent event){
        if(event==null||event.getCode()==null||isModifierOnly(event.getCode()))return ""; List<String> parts=new ArrayList<>();
        if(event.isControlDown()||event.isMetaDown())parts.add("Shortcut"); if(event.isAltDown())parts.add("Alt"); if(event.isShiftDown())parts.add("Shift");
        parts.add(event.getCode().getName()); return String.join("+",parts);
    }
    public static boolean isValidBinding(String value){String n=normalize(value);if(n.isBlank())return true;try{KeyCombination.valueOf(n);return true;}catch(Exception ignored){return false;}}

    public static List<String> validate(Map<Action,String> draft){return validate(draft,null);}
    public static List<String> validate(Map<Action,String> draft,Map<Action,Scope> scopes){
        return validateActions(draft,scopes,List.of(Action.values()));
    }

    /**
     * Validate only the catalog that a settings surface actually owns. Hidden editor-specific
     * shortcuts (PDF/Excel/Master Data) deliberately reuse keys such as F5, ENTER and DELETE
     * inside their private contexts and must not block edits in the user-facing three-group
     * Shortcut Manager.
     */
    public static List<String> validateActions(Map<Action,String> draft,Map<Action,Scope> scopes,java.util.Collection<Action> actions){
        List<Action> values=actions==null?List.of():actions.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<Action,String> effective=new EnumMap<>(Action.class); Map<Action,Scope> effectiveScopes=new EnumMap<>(Action.class);
        for(Action a:values){
            String v=draft!=null&&draft.containsKey(a)?draft.get(a):configuredBinding(a); v=normalize(v);
            if(!isValidBinding(v))return List.of(a.label()+": invalid key combination '"+v+"'."); effective.put(a,v);
            effectiveScopes.put(a,scopes!=null&&scopes.containsKey(a)?scopes.get(a):configuredScope(a));
        }
        List<String> errors=new ArrayList<>();
        for(int i=0;i<values.size();i++){
            Action a=values.get(i); String av=effective.get(a); if(av==null||av.isBlank())continue;
            for(int j=i+1;j<values.size();j++){
                Action b=values.get(j); String bv=effective.get(b); if(bv==null||bv.isBlank()||!sameBinding(av,bv))continue;
                if(scopesConflict(effectiveScopes.get(a),effectiveScopes.get(b))) errors.add(displayRaw(av)+" is assigned to both "+a.label()+" and "+b.label()+" in overlapping scopes.");
            }
        }
        return List.copyOf(errors);
    }
    private static boolean scopesConflict(Scope a,Scope b){
        if(a==null)a=Scope.GLOBAL;if(b==null)b=Scope.GLOBAL;
        return a==b||a==Scope.GLOBAL||b==Scope.GLOBAL||a==Scope.CURRENT_SCREEN||b==Scope.CURRENT_SCREEN;
    }

    public static void save(Map<Action,String> values){
        List<String> errors=validate(values);if(!errors.isEmpty())throw new IllegalArgumentException(String.join("\n",errors));
        for(Action a:Action.values())if(values!=null&&values.containsKey(a))ConfigManager.setWithoutSaving(storageKey(a),normalize(values.get(a))); ConfigManager.save(); refreshBoundLabels();
    }
    public static void saveActions(Map<Action,String> values,Map<Action,Scope> scopes,java.util.Collection<Action> actions){
        List<String> errors=validateActions(values,scopes,actions);if(!errors.isEmpty())throw new IllegalArgumentException(String.join("\n",errors));
        if(values!=null&&actions!=null)for(Action a:actions)if(a!=null&&values.containsKey(a))ConfigManager.setWithoutSaving(storageKey(a),normalize(values.get(a)));
        ConfigManager.save();
        refreshBoundLabels();
    }
    public static void reset(Action action){if(action==null)return;ConfigManager.setWithoutSaving(storageKey(action),action.defaultBinding());saveOptions(action,action.scope(),false,defaultRequireSelection(action));refreshBoundLabels();}
    private static boolean defaultRequireSelection(Action a){return a==Action.EDIT_CURRENT||a==Action.OPEN_SELECTED||a==Action.DELETE_SELECTED;}
    public static Map<Action,String> defaults(){Map<Action,String> r=new LinkedHashMap<>();for(Action a:Action.values())r.put(a,a.defaultBinding());return r;}

    /** True when the configured user scope allows this action in the current page/context. */
    public static boolean scopeActive(Action action,Object eventTarget){
        if(action==null)return false; Scope scope=configuredScope(action); if(scope==Scope.GLOBAL||scope==Scope.CURRENT_SCREEN)return true;
        if(scope==Scope.PDF_STUDIO)return withinStyle(eventTarget,"pdf-designer-root","pdf-studio-root");
        if(scope==Scope.EXCEL_STUDIO)return withinStyle(eventTarget,"excel-studio-root");
        if(scope==Scope.MASTER_DATA)return withinStyle(eventTarget,"master-data-root")||controllerContains("MasterData");
        String name=currentControllerName();
        return switch(scope){
            case SALES -> containsAny(name,"Sale","Sales","Quotation");
            case PURCHASE -> containsAny(name,"Purchase");
            case INVENTORY -> containsAny(name,"Inventory","ItemMaster");
            case CUSTOMERS -> containsAny(name,"Customer");
            case SUPPLIERS -> containsAny(name,"Supplier");
            case REPORTS -> containsAny(name,"Report");
            case COMMUNICATION -> containsAny(name,"Communication");
            case SETTINGS -> containsAny(name,"Settings");
            default -> true;
        };
    }
    private static String currentControllerName(){Object c=NavigationManager.currentController();return c==null?"":c.getClass().getSimpleName();}
    private static boolean controllerContains(String value){return currentControllerName().contains(value);}
    private static boolean containsAny(String value,String...parts){for(String p:parts)if(value.contains(p))return true;return false;}
    private static boolean withinStyle(Object target,String...styles){
        if(!(target instanceof Node node))return false;
        for(Node current=node;current!=null;current=current.getParent())for(String style:styles)if(current.getStyleClass().contains(style))return true;
        return false;
    }

    public static boolean isEditorTarget(Object target){return withinStyle(target,"excel-studio-root","pdf-designer-root","pdf-studio-root")||captureTarget(target);}
    private static boolean captureTarget(Object target){
        if(!(target instanceof Node node))return false;for(Node current=node;current!=null;current=current.getParent())if(Boolean.TRUE.equals(current.getProperties().get("dse.shortcut-capture")))return true;return false;
    }
    public static boolean textInputBlocked(Action action,Object target){return target instanceof TextInputControl&&!allowInTextInput(action);}

    private static boolean sameBinding(String a,String b){return normalize(a).equalsIgnoreCase(normalize(b));}
    private static String displayRaw(String raw){return raw.replace("Shortcut","Ctrl/Cmd");}
    private static boolean isModifierOnly(KeyCode code){return code==KeyCode.SHIFT||code==KeyCode.CONTROL||code==KeyCode.ALT||code==KeyCode.META;}
    private static String normalize(String value){
        if(value==null)return "";String raw=value.trim();if(raw.equalsIgnoreCase("disabled")||raw.equalsIgnoreCase("none"))return "";
        return raw.replaceAll("(?i)ctrl/cmd","Shortcut").replaceAll("(?i)cmd","Shortcut").replaceAll("(?i)command","Shortcut")
                .replaceAll("(?i)control","Shortcut").replaceAll("(?i)ctrl","Shortcut").replaceAll("\\s*\\+\\s*","+");
    }
}
