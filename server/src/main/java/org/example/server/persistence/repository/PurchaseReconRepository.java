package org.example.server.persistence.repository;
import jakarta.persistence.LockModeType;import org.example.server.persistence.entity.PurchaseReconEntity;import org.springframework.data.domain.Pageable;import org.springframework.data.domain.Page;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface PurchaseReconRepository extends JpaRepository<PurchaseReconEntity,Integer>{
 List<PurchaseReconEntity> findAllByOrderByInvoiceDateDescIdDesc();
 Optional<PurchaseReconEntity> findByReconRef(String ref);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from PurchaseReconEntity r where r.id=:id") Optional<PurchaseReconEntity> findByIdForUpdate(@Param("id") Integer id);
 @Query("select (count(r)>0) from PurchaseReconEntity r where r.reconSupplier.id=:supplierId and upper(trim(r.supplierInvoiceNo))=upper(trim(:invoiceNo)) and r.financialYear=:fy and (:id is null or r.id<>:id)") boolean duplicateBusinessKey(@Param("supplierId") Integer supplierId,@Param("invoiceNo") String invoiceNo,@Param("fy") String fy,@Param("id") Integer id);
 @Query("select r from PurchaseReconEntity r where (:q='' or lower(r.reconRef) like lower(concat('%',:q,'%')) or lower(r.supplierNameSnapshot) like lower(concat('%',:q,'%')) or lower(coalesce(r.supplierGstinSnapshot,'')) like lower(concat('%',:q,'%')) or lower(r.supplierInvoiceNo) like lower(concat('%',:q,'%'))) and (:status='' or upper(coalesce(r.status,''))=upper(:status))") Page<PurchaseReconEntity> searchPage(@Param("q") String q,@Param("status") String status,Pageable pageable);
 @Query("select r from PurchaseReconEntity r where (coalesce(r.invoiceValue,0)-coalesce(r.linkedAmount,0)) > 0.009 and upper(coalesce(r.status,'')) in ('OPEN','PARTIAL','NEEDS REVIEW') order by abs((coalesce(r.invoiceValue,0)-coalesce(r.linkedAmount,0))-:amount) asc,r.invoiceDate desc,r.id desc") List<PurchaseReconEntity> findOpenForBankMatching(@Param("amount") double amount,Pageable pageable);
 long countByReconSupplier_Id(Integer supplierId);
}
