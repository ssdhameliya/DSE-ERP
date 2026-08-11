package org.example.controller;

public class SupplierController extends PartyMasterController {
    @Override
    protected String partyType() {
        return "SUPPLIER";
    }

    @Override
    protected String displayName() {
        return "Supplier";
    }
}
