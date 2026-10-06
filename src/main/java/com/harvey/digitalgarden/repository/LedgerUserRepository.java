package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.LedgerUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface LedgerUserRepository extends JpaRepository<LedgerUser, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM LedgerUser u WHERE u.id = :id")
    Optional<LedgerUser> lockById(@Param("id") long id);

    Optional<LedgerUser> findByInviteCode(String inviteCode);

    Optional<LedgerUser> findByUserSn(String userSn);

    long countByUserSnStartingWith(String prefix);
}
