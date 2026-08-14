package org.example.server.persistence.repository;
import jakarta.persistence.LockModeType;import org.example.server.persistence.entity.SalesHeaderEntity;
import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.data.jpa.repository.Lock;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SalesHeaderRepository extends JpaRepository<SalesHeaderEntity,Integer>{Optional<SalesHeaderEntity> findByInvoiceNo(String invoiceNo);@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select sale from SalesHeaderEntity sale where sale.id=:id") Optional<SalesHeaderEntity> findByIdForUpdate(@Param("id") Integer id); boolean existsByInvoiceNo(String invoiceNo); boolean existsByOrderNo(String orderNo); List<SalesHeaderEntity> findAllByOrderByInvoiceDateDescIdDesc();}
