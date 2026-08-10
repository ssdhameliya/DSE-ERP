package org.example.server.persistence.repository;
import org.example.server.persistence.entity.SalesLineEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SalesLineRepository extends JpaRepository<SalesLineEntity,Integer>{List<SalesLineEntity> findBySalesIdOrderByIdAsc(Integer salesId); void deleteBySalesId(Integer salesId);}
