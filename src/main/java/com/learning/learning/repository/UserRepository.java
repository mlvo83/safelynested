package com.learning.learning.repository;


import com.learning.learning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByEnabledTrue();

    List<User> findByEnabledFalse();

    List<User> findByCharityId(Long charityId);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r " +
           "WHERE u.charity IS NOT NULL " +
           "AND r.name IN ('ROLE_CHARITY_FACILITATOR', 'CHARITY_FACILITATOR') " +
           "ORDER BY u.charity.id, u.username")
    List<User> findAllCharityFacilitators();
}