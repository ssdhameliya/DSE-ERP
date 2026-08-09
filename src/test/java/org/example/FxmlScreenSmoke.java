package org.example;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.example.config.ConfigManager;
import org.example.config.WorkspaceManager;
import org.example.controller.SalesScreenContext;
import org.example.database.DatabaseManager;
import org.example.theme.ThemeManager;
import org.example.util.PlatformUiSupport;

import java.sql.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class FxmlScreenSmoke {
    public static void main(String[] args) throws Exception {
        WorkspaceManager.initialize();
        boolean temporaryWorkspace=!WorkspaceManager.isConfigured();
        if(temporaryWorkspace)WorkspaceManager.configure(Files.createTempDirectory("dse-fxml-smoke"));
        ConfigManager.load();
        if(temporaryWorkspace)ConfigManager.set("db.url","jdbc:sqlite:"+WorkspaceManager.getDatabaseFolder().resolve("JavaAppERP.db"));
        DatabaseManager.initialize();
        try(Connection c=DatabaseManager.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT invoice_no FROM sales_header ORDER BY id DESC LIMIT 1")){if(r.next())SalesScreenContext.select(r.getString(1));}
        // Every application page and CRUD dialog is parsed and styled in both
        // themes. This catches missing controls, stale CSS and visibility drift.
        List<String> screens=List.of("BackupRestore.fxml","CommunicationCenter.fxml","Customer.fxml","Dashboard.fxml","DashboardHome.fxml","EmailSettings.fxml","Import.fxml","Inventory.fxml","Itemdialog.fxml","ItemMaster.fxml","Login.fxml","lookupDialog.fxml","Masterdata.fxml","Operations.fxml","PartyDialog.fxml","PaymentHistory.fxml","Profile.fxml","Purchase.fxml","PurchaseList.fxml","PurchaseReturnDetails.fxml","PurchaseReturns.fxml","Quotations.fxml","RecordPayment.fxml","Registration.fxml","ReminderCenter.fxml","Reports.fxml","Sale.fxml","SalesInvoiceDetails.fxml","SalesList.fxml","SalesReturns.fxml","Settings.fxml","Splash.fxml","Suppliers.fxml","UserAccess.fxml","UserDialog.fxml");
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        Platform.startup(()->{try{ThemeManager.Theme original=ThemeManager.getCurrentTheme();for(int pass=0;pass<2;pass++){String theme=ThemeManager.getCurrentTheme().name();for(String f:screens){Parent root=FXMLLoader.load(FxmlScreenSmoke.class.getResource("/fxml/pages/"+f));if(root==null)throw new IllegalStateException(f+" returned no root");Scene scene=new Scene(root,1024,768);ThemeManager.applyTheme(scene);PlatformUiSupport.installResponsiveClasses(scene);root.applyCss();root.layout();if(root.minWidth(768)>1024||root.minHeight(1024)>768)throw new IllegalStateException(f+" exceeds 1024x768 minimum layout: "+root.minWidth(768)+"x"+root.minHeight(1024));System.out.println("FXML_OK "+theme+" 1024x768 "+f);}ThemeManager.toggle(new Scene(new javafx.scene.layout.Pane()));}if(ThemeManager.getCurrentTheme()!=original)ThemeManager.toggle(new Scene(new javafx.scene.layout.Pane()));}catch(Throwable e){failure.set(e);e.printStackTrace();}finally{done.countDown();Platform.exit();}});
        if(!done.await(90,TimeUnit.SECONDS))throw new IllegalStateException("FXML smoke test timed out");DatabaseManager.close();if(failure.get()!=null)throw new RuntimeException("FXML smoke test failed",failure.get());
    }
}
