package org.example.server.persistence.repository;
import org.example.server.persistence.entity.ItemEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ItemRepository extends JpaRepository<ItemEntity,Integer> {
    List<ItemEntity> findAllByOrderByItemCodeAsc();
    Optional<ItemEntity> findByItemCode(String itemCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ItemEntity item where item.itemCode = :itemCode")
    Optional<ItemEntity> findByItemCodeForUpdate(@Param("itemCode") String itemCode);
    boolean existsByItemCode(String itemCode);
}
