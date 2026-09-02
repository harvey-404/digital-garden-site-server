package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.LedgerUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerUserRepository extends JpaRepository<LedgerUser, Long> {

    Optional<LedgerUser> findByInviteCode(String inviteCode);

    Optional<LedgerUser> findByUserSn(String userSn);

    long countByUserSnStartingWith(String prefix);
}
