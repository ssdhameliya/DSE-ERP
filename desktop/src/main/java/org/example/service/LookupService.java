package org.example.service;

import org.example.api.master.MasterApiClient;
import org.example.config.ConfigManager;
import org.example.dao.LookupDAO;
import org.example.model.Lookup;
import org.example.util.ScreenRefreshPolicy;

import java.util.*;

public class LookupService {
    private final LookupDAO dao=new LookupDAO();
    private final MasterApiClient api=new MasterApiClient();
    private boolean useApi(){return ConfigManager.isApiDataEnabled();}

    public void save(Lookup l){if(useApi())api.saveLookup(l);else dao.save(l);invalidate();}
    public void update(Lookup l){if(useApi())api.updateLookup(l);else dao.update(l);invalidate();}
    public void delete(Lookup l){if(l==null)return;if(useApi())api.deleteLookup(l.getId(),l.getRowVersion());else dao.delete(l);invalidate();}
    public void delete(int id){if(useApi())throw new IllegalStateException("Managed-server lookup delete requires the loaded row version.");else dao.delete(id);invalidate();}
    public void setActive(Lookup l,boolean active){if(l==null)return;l.setActive(active);if(useApi()){var saved=api.setLookupActive(l.getId(),active,l.getRowVersion());l.setRowVersion(saved.rowVersion());}else dao.update(l);invalidate();}
    public List<Lookup> getByType(String t){String type=key(t);return useApi()?ReferenceDataCache.getList("LOOKUP:TYPE:"+type,()->api.lookups(type),LookupService::copy):dao.getByType(type);}
    public List<Lookup> getByCategoryCode(String c){String code=key(c);return useApi()?ReferenceDataCache.getList("LOOKUP:CATEGORY:"+code,()->api.lookupsByCategoryCode(code),LookupService::copy):dao.getByCategoryCode(code);}
    public List<String> getValues(String t){String type=key(t);return useApi()?ReferenceDataCache.getStrings("LOOKUP:VALUES:"+type,()->api.lookupValues(type)):dao.getValues(type);}
    public List<String> getValuesByCategoryCode(String c){String code=key(c);return useApi()?ReferenceDataCache.getStrings("LOOKUP:CATEGORY_VALUES:"+code,()->api.lookupValuesByCategoryCode(code)):dao.getValuesByCategoryCode(code);}
    public String generateNextCode(String t){return useApi()?api.nextLookupCode(t):dao.generateNextCode(t);}
    private void invalidate(){ReferenceDataCache.invalidate("LOOKUP");ScreenRefreshPolicy.invalidateAll();}
    private static String key(String v){return v==null?"":v.trim().toUpperCase(Locale.ROOT);}
    private static Lookup copy(Lookup l){Lookup x=new Lookup();x.setId(l.getId());x.setRowVersion(l.getRowVersion());x.setLookupType(l.getLookupType());x.setLookupCode(l.getLookupCode());x.setLookupValue(l.getLookupValue());x.setDescription(l.getDescription());x.setDisplayOrder(l.getDisplayOrder());x.setActive(l.isActive());return x;}
}
