package org.example.service;

import org.example.api.master.MasterApiClient;
import org.example.config.ConfigManager;
import org.example.dao.PartyDAO;
import org.example.model.Party;

import java.util.*;

public class PartyService {
    private final PartyDAO dao = new PartyDAO();
    private final MasterApiClient api = new MasterApiClient();
    private boolean useApi(){ return ConfigManager.isApiDataEnabled(); }

    public List<Party> search(String type,String q,int limit){
        return useApi()?api.searchParties(type,q,limit):dao.getByType(type).stream().filter(p->{String h=((p.getPartyCode()==null?"":p.getPartyCode())+" "+(p.getName()==null?"":p.getName())+" "+(p.getContactPerson()==null?"":p.getContactPerson())+" "+(p.getPhone()==null?"":p.getPhone())+" "+(p.getGstin()==null?"":p.getGstin())).toLowerCase(Locale.ROOT);return q==null||q.isBlank()||h.contains(q.toLowerCase(Locale.ROOT));}).limit(Math.max(1,limit)).toList();
    }
    public void save(Party p){if(useApi())api.saveParty(p);else dao.save(p);invalidate(p==null?null:p.getPartyType());}
    public void update(Party p){if(useApi())api.updateParty(p);else dao.update(p);invalidate(p==null?null:p.getPartyType());}
    public boolean existsByCode(String c){return useApi()?api.partyExists(c):dao.existsByCode(c);}
    public void delete(Party p){if(p==null)return;if(useApi())api.deleteParty(p.getId(),p.getRowVersion());else dao.delete(p);invalidate(p.getPartyType());}
    public void delete(int id){if(useApi()){Party p=getByType("CUSTOMER").stream().filter(x->x.getId()==id).findFirst().orElseGet(()->getByType("SUPPLIER").stream().filter(x->x.getId()==id).findFirst().orElseThrow());delete(p);}else{dao.delete(id);ReferenceDataCache.invalidate("PARTY");}}
    public List<Party> getByType(String t){String type=t==null?"":t.trim().toUpperCase(Locale.ROOT);return useApi()?ReferenceDataCache.getList("PARTY:"+type,()->api.parties(type),PartyService::copy):dao.getByType(type);}
    public String nextCode(String t){return useApi()?api.nextPartyCode(t):dao.nextCode(t);}
    public void saveOrUpdate(Party p){if(existsByCode(p.getPartyCode()))update(p);else save(p);}
    private void invalidate(String type){if(type==null||type.isBlank())ReferenceDataCache.invalidate("PARTY");else ReferenceDataCache.invalidate("PARTY:"+type);}
    private static Party copy(Party p){Party x=new Party();x.setId(p.getId());x.setRowVersion(p.getRowVersion());x.setPartyType(p.getPartyType());x.setPartyCode(p.getPartyCode());x.setName(p.getName());x.setContactPerson(p.getContactPerson());x.setPhone(p.getPhone());x.setEmail(p.getEmail());x.setGstin(p.getGstin());x.setAddress(p.getAddress());x.setOpeningBalance(p.getOpeningBalance());x.setActive(p.isActive());return x;}
}
