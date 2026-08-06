package com.harvey.digitalgarden.game.semantic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.harvey.digitalgarden.dto.game.SemanticRoundAdminVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SemanticAutoStartServiceTest {

    private SemanticRoundAdminService adminService;
    private GameEngineService gameEngine;
    private SemanticAutoStartService autoStart;

    @BeforeEach
    void setUp() {
        adminService = mock(SemanticRoundAdminService.class);
        gameEngine = mock(GameEngineService.class);
        autoStart = new SemanticAutoStartService(adminService, gameEngine);
    }

    @Test
    void startsWhenWaitingWords() {
        when(gameEngine.getRoundSnapshot())
                .thenReturn(new GameEngineService.RoundSnapshot(1L, "waiting_words", false));
        SemanticRoundAdminVO vo = new SemanticRoundAdminVO();
        vo.setStatus("active");
        vo.setRoundId(2L);
        when(adminService.startRound()).thenReturn(vo);

        autoStart.tryAutoStart("test");

        verify(adminService).startRound();
    }

    @Test
    void skipsWhenActive() {
        when(gameEngine.getRoundSnapshot())
                .thenReturn(new GameEngineService.RoundSnapshot(1L, "active", true));

        autoStart.tryAutoStart("test");

        verify(adminService, never()).startRound();
    }

    @Test
    void skipsWhenFinishedByAdmin() {
        when(gameEngine.getRoundSnapshot())
                .thenReturn(new GameEngineService.RoundSnapshot(1L, "finished", false));

        autoStart.tryAutoStart("test");

        verifyNoInteractions(adminService);
    }
}
