package org.example.controller;

import org.example.navigation.ScreenLifecycle;
public class SupplierController extends PartyMasterController implements ScreenLifecycle {
    @javafx.fxml.FXML protected void openSupplier360(){
        org.example.model.Party p=tableParties.getSelectionModel().getSelectedItem();
        if(p==null){
            org.example.util.AppDialogService.warning(tableParties,"Supplier 360°","Select a supplier","Select a supplier before opening Supplier 360°.");
            return;
        }
        Supplier360Context.select(p);
        org.example.navigation.NavigationManager.navigateOrReport("/fxml/pages/Supplier360.fxml");
    }
    @Override protected String partyType(){return "SUPPLIER";}
    @Override protected String displayName(){return "Supplier";}

    @Override
    public void onScreenHidden() {
        super.onScreenHidden();
        SupplierPurchaseContext.clear();
    }
}
