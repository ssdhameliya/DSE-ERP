package org.example.server.persistence.repository;
import org.example.server.persistence.entity.PaymentRecordEntity; import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRecordRepository extends JpaRepository<PaymentRecordEntity,Integer>{}
