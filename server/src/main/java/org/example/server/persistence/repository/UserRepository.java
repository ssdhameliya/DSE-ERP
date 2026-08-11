package org.example.server.persistence.repository;

import org.example.server.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    @Query("""
        select u from UserEntity u left join fetch u.assignedRole r
        where (lower(u.username)=lower(:identity) or lower(u.email)=lower(:identity))
          and u.active=1 and coalesce(u.locked,0)=0
          and (r is null or r.active=1)
        """)
    Optional<UserEntity> findActiveByIdentity(@Param("identity") String identity);
}
