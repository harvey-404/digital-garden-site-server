package com.harvey.digitalgarden.game.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps a round running when the queue has pending words:
 * boot / refill after waiting_words. Does not restart after an admin stop (finished).
 * Guess-correct auto-next remains in {@link GameEngineService}.
 */
@Component
public class SemanticAutoStartService {

    private static final Logger log = LoggerFactory.getLogger(SemanticAutoStartService.class);

    private final SemanticRoundAdminService adminService;
    private final GameEngineService gameEngine;

    public SemanticAutoStartService(
            SemanticRoundAdminService adminService, GameEngineService gameEngine) {
        this.adminService = adminService;
        this.gameEngine = gameEngine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        tryAutoStart("boot");
    }

    @EventListener(SemanticQueueRefilledEvent.class)
    public void onQueueRefilled() {
        tryAutoStart("queue-refilled");
    }

    /**
     * Only starts when idle or waiting_words (not after admin stop → finished).
     */
    public void tryAutoStart(String reason) {
        GameEngineService.RoundSnapshot snap = gameEngine.getRoundSnapshot();
        String status = snap.status() == null ? "" : snap.status();
        if ("active".equals(status) || "finished".equals(status)) {
            return;
        }
        try {
            var vo = adminService.startRound();
            log.info(
                    "semantic auto-start ({}) -> status={} roundId={}",
                    reason,
                    vo.getStatus(),
                    vo.getRoundId());
        } catch (Exception e) {
            log.warn("semantic auto-start skipped ({}): {}", reason, e.getMessage());
        }
    }
}
