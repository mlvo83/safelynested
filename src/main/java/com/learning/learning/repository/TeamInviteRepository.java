package com.learning.learning.repository;

import com.learning.learning.entity.TeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

    Optional<TeamInvite> findByToken(String token);

    List<TeamInvite> findByCharityIdOrderByCreatedAtDesc(Long charityId);

    List<TeamInvite> findByCharityIdAndStatus(Long charityId, TeamInvite.InviteStatus status);

    long countByCharityIdAndStatus(Long charityId, TeamInvite.InviteStatus status);

    boolean existsByEmailAndCharityIdAndStatus(String email, Long charityId, TeamInvite.InviteStatus status);
}
