package org.example.server.persistence.repository;
import org.example.server.persistence.entity.SalesHeaderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SalesHeaderRepository extends JpaRepository<SalesHeaderEntity,Integer>{Optional<SalesHeaderEntity> findByInvoiceNo(String invoiceNo); boolean existsByInvoiceNo(String invoiceNo); boolean existsByOrderNo(String orderNo); List<SalesHeaderEntity> findAllByOrderByInvoiceDateDescIdDesc();}
