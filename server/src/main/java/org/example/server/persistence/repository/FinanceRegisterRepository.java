package org.example.server.persistence.repository;
import org.example.server.persistence.entity.FinanceRegisterEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface FinanceRegisterRepository extends JpaRepository<FinanceRegisterEntity,Integer>{List<FinanceRegisterEntity> findAllByOrderByVoucherDateDescIdDesc(); Optional<FinanceRegisterEntity> findTopByOrderByIdDesc();}
