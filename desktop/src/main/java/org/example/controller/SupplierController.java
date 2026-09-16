package org.example.controller;

public class SupplierController extends PartyMasterController {
    @javafx.fxml.FXML protected void openSupplier360(){
        org.example.model.Party p=tableParties.getSelectionModel().getSelectedItem();
        if(p==null){
            org.example.util.ModernDialog.warning(tableParties,"Supplier 360°","Select a supplier","Select a supplier before opening Supplier 360°.");
            return;
        }
        Supplier360Context.select(p);
        org.example.navigation.NavigationManager.navigateOrReport("/fxml/pages/Supplier360.fxml");
    }
    @Override protected String partyType(){return "SUPPLIER";}
    @Override protected String displayName(){return "Supplier";}
}
