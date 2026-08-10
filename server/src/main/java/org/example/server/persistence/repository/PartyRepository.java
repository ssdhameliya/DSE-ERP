package org.example.server.persistence.repository;
import org.example.server.persistence.entity.PartyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PartyRepository extends JpaRepository<PartyEntity,Integer> {
    List<PartyEntity> findByPartyTypeOrderByNameAsc(String partyType);
    boolean existsByPartyCode(String partyCode);
    long countByPartyType(String partyType);
}
