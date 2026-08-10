package org.example.server.persistence.repository;
import org.example.server.persistence.entity.LookupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface LookupRepository extends JpaRepository<LookupEntity,Integer> {
    List<LookupEntity> findByLookupTypeOrderByDisplayOrderAscLookupValueAsc(String lookupType);
    @Query("select l from LookupEntity l where l.lookupType=:lookupType and coalesce(l.active,1)=1 order by l.displayOrder asc, l.lookupValue asc")
    List<LookupEntity> findByLookupTypeAndActiveTrueOrderByDisplayOrderAscLookupValueAsc(@Param("lookupType") String lookupType);
    List<LookupEntity> findByLookupTypeOrderByLookupCodeDesc(String lookupType);
    void deleteByLookupType(String lookupType);
    long countByLookupType(String lookupType);
}
