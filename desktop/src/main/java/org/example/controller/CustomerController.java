package org.example.controller;

import javafx.fxml.FXML;
import org.example.model.Party;
import org.example.navigation.NavigationManager;
import org.example.util.ModernDialog;

public class CustomerController extends PartyMasterController {
    @Override protected String partyType(){return "CUSTOMER";}
    @Override protected String displayName(){return "Customer";}

    @FXML protected void openCustomer360(){
        Party party=tableParties.getSelectionModel().getSelectedItem();
        if(party==null){ModernDialog.warning(tableParties,"Customer 360°","Select a customer","Select a customer before opening Customer 360°.");return;}
        Customer360Context.select(party);
        NavigationManager.navigateOrReport("/fxml/pages/Customer360.fxml");
    }
}
