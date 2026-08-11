package org.example.dao;

import org.example.api.master.MasterApiClient;
import org.example.model.Party;
import java.util.List;

/** Compatibility DAO backed by the typed Spring master-data API. */
public class PartyDAO {
    private final MasterApiClient api = new MasterApiClient();

    public void save(Party party) { api.saveParty(party); }
    public void update(Party party) { api.updateParty(party); }
    public void delete(int id) { api.deleteParty(id); }
    public List<Party> getByType(String type) { return api.parties(type); }
    public String nextCode(String type) { return api.nextPartyCode(type); }
    public boolean existsByCode(String code) { return api.partyExists(code); }
}
