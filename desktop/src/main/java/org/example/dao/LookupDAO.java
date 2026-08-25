package org.example.dao;

import org.example.api.master.MasterApiClient;
import org.example.model.Lookup;
import java.util.List;

/** Compatibility DAO backed by the typed Spring master-data API. */
public class LookupDAO {
    private final MasterApiClient api = new MasterApiClient();

    public void save(Lookup lookup) { api.saveLookup(lookup); }
    public void update(Lookup lookup) { api.updateLookup(lookup); }
    public void delete(Lookup lookup) { if (lookup == null) return; api.deleteLookup(lookup.getId(), lookup.getRowVersion()); }
    public void delete(int id) { throw new IllegalStateException("Lookup deletion requires the loaded row version."); }
    public List<Lookup> getByType(String type) { return api.lookups(type); }
    public List<String> getValues(String type) { return api.lookupValues(type); }
    public List<String> getValuesByCategoryCode(String categoryCode) { return api.lookupValuesByCategoryCode(categoryCode); }
    public List<Lookup> getByCategoryCode(String categoryCode) { return api.lookupsByCategoryCode(categoryCode); }
    public String generateNextCode(String type) { return api.nextLookupCode(type); }
}
