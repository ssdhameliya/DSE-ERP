package org.example.server.persistence.repository;

import org.example.server.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity,Integer> {
    Optional<RoleEntity> findByNameIgnoreCase(String name);
}
