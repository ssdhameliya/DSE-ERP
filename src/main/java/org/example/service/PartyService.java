package org.example.service;

import org.example.dao.PartyDAO;
import org.example.model.Party;

import java.util.List;

public class PartyService {
    private final PartyDAO dao = new PartyDAO();

    public void save(Party party) {
        dao.save(party);
    }

    public void update(Party party) {
        dao.update(party);
    }

    // Check if item exists
    public boolean existsByCode(String code) {
        return dao.existsByCode(code);
    }

    public void delete(int id) {
        dao.delete(id);
    }

    public List<Party> getByType(String type) {
        return dao.getByType(type);
    }

    public String nextCode(String type) {
        return dao.nextCode(type);
    }

    // NEW: saveOrUpdate
    public void saveOrUpdate(Party party) {
        if (dao.existsByCode(party.getPartyCode())) {
            dao.update(party);
        } else {
            dao.save(party);
        }
    }
}
