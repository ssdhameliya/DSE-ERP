package org.example.server.runtime;

import org.example.server.persistence.JpaNativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeService {
    private final JpaNativeRepository repository;

    public RuntimeService(JpaNativeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean databaseReady() {
        Integer one = repository.queryForObject("SELECT 1", Integer.class);
        return one != null && one == 1;
    }
}
