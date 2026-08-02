package org.example;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.example.config.ConfigManager;
import org.example.controller.SalesScreenContext;
import org.example.database.DatabaseManager;
import org.example.theme.ThemeManager;

import java.sql.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class FxmlScreenSmoke {
    public static void main(String[] args) throws Exception {
        ConfigManager.load();DatabaseManager.initialize();
        try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT invoice_no FROM sales_header ORDER BY id DESC LIMIT 1")){if(r.next())SalesScreenContext.select(r.getString(1));}
        // Every application page and CRUD dialog is parsed and styled in both
        // themes. This catches missing controls, stale CSS and visibility drift.
        List<String> screens=List.of("BackupRestore.fxml","CommunicationCenter.fxml","Customer.fxml","Dashboard.fxml","DashboardHome.fxml","EmailSettings.fxml","Import.fxml","Inventory.fxml","Itemdialog.fxml","ItemMaster.fxml","Login.fxml","lookupDialog.fxml","Masterdata.fxml","Operations.fxml","PartyDialog.fxml","PaymentHistory.fxml","Profile.fxml","Purchase.fxml","PurchaseList.fxml","PurchaseReturnDetails.fxml","PurchaseReturns.fxml","Quotations.fxml","RecordPayment.fxml","Registration.fxml","ReminderCenter.fxml","Reports.fxml","Sale.fxml","SalesInvoiceDetails.fxml","SalesList.fxml","SalesReturns.fxml","Settings.fxml","Splash.fxml","Suppliers.fxml","UserAccess.fxml","UserDialog.fxml");
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Platform.startup(()->{try{ThemeManager.Theme original=ThemeManager.getCurrentTheme();for(int pass=0;pass<2;pass++){String theme=ThemeManager.getCurrentTheme().name();for(String f:screens){Parent root=FXMLLoader.load(FxmlScreenSmoke.class.getResource("/fxml/pages/"+f));if(root==null)throw new IllegalStateException(f+" returned no root");Scene scene=new Scene(root);ThemeManager.applyTheme(scene);root.applyCss();System.out.println("FXML_OK "+theme+" "+f);}ThemeManager.toggle(new Scene(new javafx.scene.layout.Pane()));}if(ThemeManager.getCurrentTheme()!=original)ThemeManager.toggle(new Scene(new javafx.scene.layout.Pane()));}catch(Throwable e){failure.set(e);e.printStackTrace();}finally{done.countDown();Platform.exit();}});
        if(!done.await(90,TimeUnit.SECONDS))throw new IllegalStateException("FXML smoke test timed out");if(failure.get()!=null)throw new RuntimeException("FXML smoke test failed",failure.get());
    }
}
