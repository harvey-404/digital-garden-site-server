package com.harvey.digitalgarden.game.semantic;

import com.harvey.digitalgarden.entity.GameDeviceIdentity;
import com.harvey.digitalgarden.repository.GameDeviceIdentityRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists device fingerprint ↔ nickname. Same fp reuses the prior nick unless
 * rename cooldown has elapsed.
 */
@Service
public class DeviceIdentityService {

    public record ResolveResult(String username, boolean reusedPrior, boolean isNewDevice) {}

    private final GameDeviceIdentityRepository repo;
    private final long nicknameCooldownSeconds;

    public DeviceIdentityService(
            GameDeviceIdentityRepository repo,
            @Value("${app.game.semantic.nickname-cooldown-seconds}") long nicknameCooldownSeconds) {
        this.repo = repo;
        this.nicknameCooldownSeconds = Math.max(0L, nicknameCooldownSeconds);
    }

    public String findUsername(String fp) {
        if (fp == null || fp.isBlank()) {
            return "";
        }
        return repo.findByFp(fp.trim())
                .map(GameDeviceIdentity::getUsername)
                .map(u -> u == null ? "" : u.trim())
                .orElse("");
    }

    @Transactional
    public ResolveResult resolveOnConnect(String fp, String requestedUsername) {
        String device = fp == null ? "" : fp.trim();
        String requested = requestedUsername == null ? "" : requestedUsername.trim();
        long now = Instant.now().getEpochSecond();

        GameDeviceIdentity row = repo.findByFp(device).orElse(null);
        if (row == null) {
            GameDeviceIdentity created = new GameDeviceIdentity();
            created.setFp(device);
            created.setUsername(requested);
            created.setBoundAt(now);
            repo.save(created);
            return new ResolveResult(requested, false, true);
        }

        String prior = row.getUsername() == null ? "" : row.getUsername().trim();
        if (prior.isEmpty()) {
            row.setUsername(requested);
            row.setBoundAt(now);
            repo.save(row);
            return new ResolveResult(requested, false, false);
        }

        if (prior.equals(requested)) {
            // Same nick — refresh bind time lightly so relationship stays warm
            row.setBoundAt(now);
            repo.save(row);
            return new ResolveResult(prior, false, false);
        }

        long boundAt = row.getBoundAt() == null ? 0L : row.getBoundAt();
        long elapsed = now - boundAt;
        if (elapsed < nicknameCooldownSeconds) {
            return new ResolveResult(prior, true, false);
        }

        row.setUsername(requested);
        row.setBoundAt(now);
        repo.save(row);
        return new ResolveResult(requested, false, false);
    }
}
