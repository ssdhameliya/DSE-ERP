package org.example.shortcut;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TableView;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeView;
import org.example.navigation.NavigationManager;
import org.example.shortcut.ShortcutRegistry.Action;
import org.example.util.ToastManager;

import java.lang.reflect.Method;
import java.util.*;

/** Executes page-level shortcuts by reusing the current page's existing buttons/controller actions. */
public final class ApplicationCommandDispatcher {
    private ApplicationCommandDispatcher(){}
    public static boolean execute(Action action){
        Spec spec=spec(action); if(spec==null)return false; Node root=NavigationManager.currentNode(); Object controller=NavigationManager.currentController();
        if(root!=null){ButtonBase button=findButton(root,spec.labels);if(button!=null){button.fire();return true;}}
        if(controller!=null&&invoke(controller,spec.methods))return true;
        if(root!=null)ToastManager.info(root,"Shortcut unavailable",spec.display+" is not available on this screen.");
        return false;
    }

    /** Best-effort generic selection check used by user-configurable advanced shortcut rules. */
    public static boolean hasSelection(){Node root=NavigationManager.currentNode();return root!=null&&hasSelection(root);}
    private static boolean hasSelection(Node node){
        if(node instanceof TableView<?> table&&table.getSelectionModel()!=null&&table.getSelectionModel().getSelectedIndex()>=0)return true;
        if(node instanceof ListView<?> list&&list.getSelectionModel()!=null&&list.getSelectionModel().getSelectedIndex()>=0)return true;
        if(node instanceof TreeView<?> tree&&tree.getSelectionModel()!=null&&tree.getSelectionModel().getSelectedIndex()>=0)return true;
        if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())if(hasSelection(child))return true;
        return false;
    }
    private static ButtonBase findButton(Node root,List<String> labels){List<ButtonBase> buttons=new ArrayList<>();collect(root,buttons);for(String wanted:labels){String w=norm(wanted);for(ButtonBase b:buttons){if(!b.isVisible()||b.isDisable())continue;String t=norm(b.getText());if(t.equals(w)||t.startsWith(w+" ")||t.startsWith(w+" ("))return b;}}return null;}
    private static void collect(Node n,List<ButtonBase> out){if(n instanceof ButtonBase b)out.add(b);if(n instanceof Parent p)for(Node c:p.getChildrenUnmodifiable())collect(c,out);}
    private static boolean invoke(Object controller,List<String> names){for(String name:names){for(Class<?> c=controller.getClass();c!=null;c=c.getSuperclass()){try{Method m=c.getDeclaredMethod(name);if(m.getParameterCount()!=0)continue;m.setAccessible(true);m.invoke(controller);return true;}catch(NoSuchMethodException ignored){}catch(Exception ex){throw new IllegalStateException("Shortcut action failed: "+name,ex);}}}return false;}
    private static String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    private static Spec spec(Action a){return switch(a){
        case SAVE_CURRENT->new Spec("Save",List.of("Save","Save Changes","Save Settings","Update"),List.of("save","saveChanges","saveCurrent"));
        case EDIT_CURRENT->new Spec("Edit",List.of("Edit","Edit Sale","Edit Purchase"),List.of("editSelectedSale","editSelectedPurchase","editSelected","editCurrent"));
        case REFRESH_CURRENT->new Spec("Refresh",List.of("Refresh","Reload"),List.of("refresh","loadData","load","loadItems","reload"));
        case NEW_CURRENT->new Spec("New",List.of("New","Add New","Create New","New Sale","New Purchase","New Item","Add Customer","Add Supplier"),List.of("newEntry","newParty","createNew","addNew","createSale","createPurchase","add"));
        case OPEN_SELECTED->new Spec("Open",List.of("View","Open","View Details","Details"),List.of("openSelected","viewSelected","showSelected","openCurrent"));
        case DELETE_SELECTED->new Spec("Delete",List.of("Delete","Remove"),List.of("deleteSelected","removeSelected"));
        case PRINT_CURRENT->new Spec("Print",List.of("Print","Print / Preview","Preview"),List.of("print","printSelected","preview"));
        case EXPORT_CURRENT->new Spec("Export",List.of("Export","Export Excel","Excel","View / Download Excel"),List.of("export","exportExcel","excelSelected"));
        case CLOSE_BACK->new Spec("Close / Back",List.of("Close","Cancel","Back"),List.of("closeDetails","cancel","back"));
        case MASTER_DELETE->new Spec("Delete Master",List.of("Delete","Remove"),List.of("deleteLookup","deleteSelected","removeSelected"));
        case MASTER_EDIT->new Spec("Edit Master",List.of("Edit","Edit Master"),List.of("editLookup","editSelected","editCurrent"));
        case MASTER_REFRESH->new Spec("Refresh Master",List.of("Refresh","Reload"),List.of("loadTable","loadCategories","refresh","loadData"));
        case MASTER_NEW->new Spec("New Master",List.of("Add Master","New Master","New"),List.of("addLookup","newEntry","addNew"));
        default->null;};}
    private record Spec(String display,List<String> labels,List<String> methods){}
}
