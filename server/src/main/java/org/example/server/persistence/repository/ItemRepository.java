package org.example.server.persistence.repository;
import org.example.server.persistence.entity.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ItemRepository extends JpaRepository<ItemEntity,Integer> {
    List<ItemEntity> findAllByOrderByItemCodeAsc();
    Optional<ItemEntity> findByItemCode(String itemCode);
    boolean existsByItemCode(String itemCode);
}
