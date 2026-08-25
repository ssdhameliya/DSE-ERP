package org.example.dao;

import org.example.api.master.MasterApiClient;
import org.example.model.Party;
import java.util.List;

/** Compatibility DAO backed by the typed Spring master-data API. */
public class PartyDAO {
    private final MasterApiClient api = new MasterApiClient();

    public void save(Party party) { api.saveParty(party); }
    public void update(Party party) { api.updateParty(party); }
    public void delete(Party party) { if (party == null) return; api.deleteParty(party.getId(), party.getRowVersion()); }
    public void delete(int id) {
        Party party = getByType("CUSTOMER").stream().filter(x -> x.getId() == id).findFirst()
                .orElseGet(() -> getByType("SUPPLIER").stream().filter(x -> x.getId() == id).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Party not found: " + id)));
        delete(party);
    }
    public List<Party> getByType(String type) { return api.parties(type); }
    public String nextCode(String type) { return api.nextPartyCode(type); }
    public boolean existsByCode(String code) { return api.partyExists(code); }
}
