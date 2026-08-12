package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.GameDeviceIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDeviceIdentityRepository extends JpaRepository<GameDeviceIdentity, Long> {
    Optional<GameDeviceIdentity> findByFp(String fp);
}
