package org.example.persistence.repository;

import org.example.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    @Query("""
        select u from UserEntity u join fetch u.assignedRole r
        where (lower(u.username)=lower(:identity) or lower(u.email)=lower(:identity))
          and u.active=1 and coalesce(u.locked,0)=0 and r.active=1
        """)
    Optional<UserEntity> findActiveByIdentity(@Param("identity") String identity);

    @Modifying @Transactional
    @Query("update UserEntity u set u.password=:password where u.id=:id")
    int updatePassword(@Param("id") int id, @Param("password") String password);
}
