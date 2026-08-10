package org.example.server.persistence.repository;
import org.example.server.persistence.entity.MasterCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MasterCategoryRepository extends JpaRepository<MasterCategoryEntity,Integer> {
    List<MasterCategoryEntity> findAllByOrderByDisplayOrderAscCategoryNameAsc();
    Optional<MasterCategoryEntity> findByCategoryName(String categoryName);
    Optional<MasterCategoryEntity> findByCategoryCode(String categoryCode);
}
