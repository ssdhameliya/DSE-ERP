package org.example.server.persistence.repository;
import org.example.server.persistence.entity.WorkflowDocumentLineEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface WorkflowDocumentLineRepository extends JpaRepository<WorkflowDocumentLineEntity,Integer>{ List<WorkflowDocumentLineEntity> findByDocumentIdOrderByLineNo(Integer id); void deleteByDocumentId(Integer id); }
