package org.example.server.persistence.repository;
import org.example.server.persistence.entity.WorkflowDocumentEntity; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.util.*;
public interface WorkflowDocumentRepository extends JpaRepository<WorkflowDocumentEntity,Integer>{
 List<WorkflowDocumentEntity> findByDocumentTypeOrderByDocumentDateDescIdDesc(String type);
 Optional<WorkflowDocumentEntity> findByDocumentTypeAndDocumentNo(String type,String no);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from WorkflowDocumentEntity w where w.id=:id") Optional<WorkflowDocumentEntity> findByIdForUpdate(@Param("id") Integer id);
}
