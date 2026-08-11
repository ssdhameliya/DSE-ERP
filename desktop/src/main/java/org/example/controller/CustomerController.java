package org.example.controller;

public class CustomerController extends PartyMasterController {
    @Override
    protected String partyType() {
        return "CUSTOMER";
    }

    @Override
    protected String displayName() {
        return "Customer";
    }
}
