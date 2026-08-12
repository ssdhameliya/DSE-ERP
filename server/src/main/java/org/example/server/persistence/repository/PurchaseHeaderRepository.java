package org.example.server.persistence.repository;
import org.example.server.persistence.entity.PurchaseHeaderEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PurchaseHeaderRepository extends JpaRepository<PurchaseHeaderEntity,Integer>{Optional<PurchaseHeaderEntity> findByInvoiceNo(String invoiceNo); boolean existsByInvoiceNo(String invoiceNo); List<PurchaseHeaderEntity> findAllByOrderByInvoiceDateDescIdDesc();}
