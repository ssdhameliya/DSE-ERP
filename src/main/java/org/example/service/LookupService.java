package org.example.service;

import org.example.dao.LookupDAO;
import org.example.model.Lookup;

import java.util.List;

public class LookupService {

    private final LookupDAO dao = new LookupDAO();

    public void save(Lookup lookup) {

        dao.save(lookup);

    }

    public void update(Lookup lookup) {

        dao.update(lookup);

    }

    public void delete(int id) {

        dao.delete(id);

    }

    public List<Lookup> getByType(String type) {

        return dao.getByType(type);

    }

    public List<String> getValues(String type) {

        return dao.getValues(type);

    }

    public String generateNextCode(String type) {

        return dao.generateNextCode(type);

    }

}