package com.omobio.admin.repository;

import com.omobio.admin.domain.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link AdminSession}.
 */
@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {

    /**
     * Find all active sessions for a user.
     */
    List<AdminSession> findByAdminUserIdAndStatus(String adminUserId, String status);

    /**
     * Find a session by token family.
     */
    List<AdminSession> findByTokenFamilyId(String tokenFamilyId);
}
