package com.omobio.admin.repository;

import com.omobio.admin.domain.AdminMfaBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminMfaBackupCodeRepository extends JpaRepository<AdminMfaBackupCode, Long> {

    List<AdminMfaBackupCode> findByAdminUserId(String adminUserId);

    Optional<AdminMfaBackupCode> findByAdminUserIdAndHashedCode(String adminUserId, String hashedCode);

    @Modifying
    @Query("DELETE FROM AdminMfaBackupCode b WHERE b.adminUserId = :userId")
    void deleteByAdminUserId(@Param("userId") String userId);

    long countByAdminUserIdAndUsedAtIsNull(String adminUserId);
}
