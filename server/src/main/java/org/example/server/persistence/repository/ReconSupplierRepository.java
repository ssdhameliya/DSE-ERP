package org.example.server.persistence.repository;
import org.example.server.persistence.entity.ReconSupplierEntity;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface ReconSupplierRepository extends JpaRepository<ReconSupplierEntity,Integer>{
 List<ReconSupplierEntity> findAllByOrderByLegalNameAscIdAsc();
 Optional<ReconSupplierEntity> findByReconSupplierRef(String ref);
 @Query("select r from ReconSupplierEntity r where upper(trim(coalesce(r.gstin,'')))=upper(trim(:gstin))") Optional<ReconSupplierEntity> findByNormalizedGstin(@Param("gstin") String gstin);
 @Query(value="select * from recon_supplier r where regexp_replace(upper(coalesce(r.legal_name,'')), '[^A-Z0-9]', '', 'g')=:nameKey order by r.id asc",nativeQuery=true) List<ReconSupplierEntity> findByNormalizedNameKey(@Param("nameKey") String nameKey);
}
