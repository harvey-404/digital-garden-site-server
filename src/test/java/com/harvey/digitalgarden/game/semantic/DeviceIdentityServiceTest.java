package com.harvey.digitalgarden.game.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harvey.digitalgarden.entity.GameDeviceIdentity;
import com.harvey.digitalgarden.repository.GameDeviceIdentityRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceIdentityServiceTest {

    @Mock GameDeviceIdentityRepository repo;

    private DeviceIdentityService service;

    @BeforeEach
    void setUp() {
        service = new DeviceIdentityService(repo, 60);
    }

    @Test
    void newDevice_bindsRequestedNick() {
        when(repo.findByFp("fp-1")).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = service.resolveOnConnect("fp-1", "Alice");
        assertEquals("Alice", r.username());
        assertFalse(r.reusedPrior());
        assertTrue(r.isNewDevice());
        verify(repo).save(any(GameDeviceIdentity.class));
    }

    @Test
    void sameNick_keepsBinding() {
        GameDeviceIdentity row = row("fp-1", "Alice", Instant.now().getEpochSecond() - 10);
        when(repo.findByFp("fp-1")).thenReturn(Optional.of(row));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = service.resolveOnConnect("fp-1", "Alice");
        assertEquals("Alice", r.username());
        assertFalse(r.reusedPrior());
    }

    @Test
    void renameWithinCooldown_reusesPrior() {
        GameDeviceIdentity row = row("fp-1", "Alice", Instant.now().getEpochSecond() - 10);
        when(repo.findByFp("fp-1")).thenReturn(Optional.of(row));

        var r = service.resolveOnConnect("fp-1", "Bob");
        assertEquals("Alice", r.username());
        assertTrue(r.reusedPrior());
        verify(repo, never()).save(any());
    }

    @Test
    void renameAfterCooldown_updatesBinding() {
        GameDeviceIdentity row = row("fp-1", "Alice", Instant.now().getEpochSecond() - 120);
        when(repo.findByFp("fp-1")).thenReturn(Optional.of(row));
        AtomicReference<GameDeviceIdentity> saved = new AtomicReference<>();
        when(repo.save(any())).thenAnswer(inv -> {
            GameDeviceIdentity g = inv.getArgument(0);
            saved.set(g);
            return g;
        });

        var r = service.resolveOnConnect("fp-1", "Bob");
        assertEquals("Bob", r.username());
        assertFalse(r.reusedPrior());
        ArgumentCaptor<GameDeviceIdentity> cap = ArgumentCaptor.forClass(GameDeviceIdentity.class);
        verify(repo).save(cap.capture());
        assertEquals("Bob", cap.getValue().getUsername());
    }

    private static GameDeviceIdentity row(String fp, String username, long boundAt) {
        GameDeviceIdentity g = new GameDeviceIdentity();
        g.setId(1L);
        g.setFp(fp);
        g.setUsername(username);
        g.setBoundAt(boundAt);
        return g;
    }
}
