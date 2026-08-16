package org.example.server.persistence.repository;
import org.example.server.persistence.entity.PartyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface PartyRepository extends JpaRepository<PartyEntity,Integer> {
    List<PartyEntity> findByPartyTypeOrderByNameAsc(String partyType);
    @Query("select party from PartyEntity party where upper(party.partyType)=upper(:type) and coalesce(party.active,1)=1 and (:q='' or lower(coalesce(party.partyCode,'')) like lower(concat('%',:q,'%')) or lower(coalesce(party.name,'')) like lower(concat('%',:q,'%')) or lower(coalesce(party.contactPerson,'')) like lower(concat('%',:q,'%')) or lower(coalesce(party.phone,'')) like lower(concat('%',:q,'%')) or lower(coalesce(party.gstin,'')) like lower(concat('%',:q,'%'))) order by party.name asc,party.id asc")
    List<PartyEntity> searchActive(@Param("type") String type,@Param("q") String q,Pageable pageable);
    boolean existsByPartyCode(String partyCode);
    long countByPartyType(String partyType);
}
