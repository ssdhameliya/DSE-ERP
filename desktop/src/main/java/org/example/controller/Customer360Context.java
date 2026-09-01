package org.example.controller;
import org.example.model.Party;
/** One-shot selected-customer handoff from Customer Master into Customer 360°. */
public final class Customer360Context {
    private static Party pending;
    private Customer360Context(){}
    public static synchronized void select(Party p){pending=copy(p);}
    public static synchronized Party consume(){Party p=pending;pending=null;return p;}
    private static Party copy(Party p){if(p==null)return null;Party x=new Party();x.setId(p.getId());x.setRowVersion(p.getRowVersion());x.setPartyType(p.getPartyType());x.setPartyCode(p.getPartyCode());x.setName(p.getName());x.setContactPerson(p.getContactPerson());x.setPhone(p.getPhone());x.setEmail(p.getEmail());x.setGstin(p.getGstin());x.setAddress(p.getAddress());x.setOpeningBalance(p.getOpeningBalance());x.setActive(p.isActive());return x;}
}
