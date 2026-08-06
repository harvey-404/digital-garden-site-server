package com.harvey.digitalgarden.game.semantic;

import com.harvey.digitalgarden.entity.GameSemanticGuess;
import com.harvey.digitalgarden.entity.GameSemanticRound;
import com.harvey.digitalgarden.entity.GameSemanticWord;
import com.harvey.digitalgarden.repository.GameSemanticGuessRepository;
import com.harvey.digitalgarden.repository.GameSemanticRoundRepository;
import com.harvey.digitalgarden.repository.GameSemanticWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameEngineServiceTest {

    @Mock GameSemanticWordRepository wordRepo;
    @Mock GameSemanticRoundRepository roundRepo;
    @Mock GameSemanticGuessRepository guessRepo;

    private final AtomicInteger embedCalls = new AtomicInteger();
    private final AtomicLong idSeq = new AtomicLong(1);

    private EmbeddingPort embedder;
    private GameEngineService engine;

    @BeforeEach
    void setUp() {
        embedCalls.set(0);
        embedder = text -> {
            embedCalls.incrementAndGet();
            return vectorFor(text);
        };
        engine = new GameEngineService(wordRepo, roundRepo, guessRepo, embedder);

        lenient().when(guessRepo.save(any(GameSemanticGuess.class))).thenAnswer(inv -> {
            GameSemanticGuess g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId(idSeq.incrementAndGet());
            }
            return g;
        });
        lenient().when(roundRepo.save(any(GameSemanticRound.class))).thenAnswer(inv -> {
            GameSemanticRound r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(idSeq.incrementAndGet());
            }
            return r;
        });
        lenient().when(wordRepo.save(any(GameSemanticWord.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(guessRepo.findByRoundIdOrderByScoreDescInDtmAsc(anyLong()))
                .thenReturn(List.of());
    }

    private static float[] vectorFor(String text) {
        // Target "苹果" → [1,0]; other words → deterministic direction by hash for varied scores
        if ("苹果".equals(text)) {
            return new float[]{1f, 0f};
        }
        if ("香蕉".equals(text)) {
            return new float[]{0.8f, 0.6f}; // cos=0.8 → score 79.99
        }
        if ("精确".equals(text)) {
            return new float[]{0.5f, (float) Math.sqrt(0.75)}; // cos=0.5 → 50.0? round(4999.5)/100
        }
        // Top10 filler: word "w0".."wN" → cosine ≈ 0.70 - i*0.01 mapped via x-component
        if (text.startsWith("w") && text.length() > 1) {
            try {
                int i = Integer.parseInt(text.substring(1));
                float c = Math.max(0f, 0.70f - i * 0.01f);
                float y = (float) Math.sqrt(Math.max(0f, 1f - c * c));
                return new float[]{c, y};
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return new float[]{0f, 1f}; // orthogonal → 0
    }

    private GameSemanticRound activeRound(long id, String target, long wordId) {
        GameSemanticRound r = new GameSemanticRound();
        r.setId(id);
        r.setTargetWord(target);
        r.setWordId(wordId);
        r.setStatus("active");
        r.setStartedAt(1L);
        return r;
    }

    private GameSemanticRound loadAppleRound() {
        GameSemanticRound round = activeRound(10L, "苹果", 1L);
        engine.loadActiveRound(round);
        return round;
    }

    @Test
    void firstDiscovererLocksWord_secondGuessSkipsEmbed() throws EmbeddingException {
        loadAppleRound();
        int afterLoad = embedCalls.get();

        GameEngineService.GuessResult first = engine.processGuess("alice", "fp-a", "香蕉");
        assertTrue(first.isFirst());
        assertFalse(first.alreadyGuessed());
        assertEquals("香蕉", first.record().word());
        assertEquals("alice", first.record().firstUser());
        assertEquals("fp-a", first.record().firstFp());
        assertEquals(79.99, first.record().score(), 1e-6);
        assertEquals(afterLoad + 1, embedCalls.get());
        verify(guessRepo, times(1)).save(any(GameSemanticGuess.class));

        GameEngineService.GuessResult second = engine.processGuess("bob", "fp-b", "香蕉");
        assertFalse(second.isFirst());
        assertTrue(second.alreadyGuessed());
        assertEquals(first.record().score(), second.record().score(), 1e-6);
        assertEquals("alice", second.record().firstUser());
        assertEquals("fp-a", second.record().firstFp());
        assertFalse(second.needBroadcastTop10());
        assertEquals(afterLoad + 1, embedCalls.get(), "second guess must not call embed");
        verify(guessRepo, times(1)).save(any(GameSemanticGuess.class));
    }

    @Test
    void top10KeepsSizeAtMost10AndSortedDesc() throws EmbeddingException {
        loadAppleRound();

        for (int i = 0; i < 11; i++) {
            engine.processGuess("u" + i, "fp" + i, "w" + i);
        }

        List<GameEngineService.WordRecord> top = engine.getTop10();
        assertEquals(10, top.size());
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).score() >= top.get(i).score(),
                    "expected desc order at index " + i);
        }
        // Highest should be w0 (cosine 0.70)
        assertEquals("w0", top.get(0).word());
        // Lowest of top10 should be w9; w10 dropped
        assertTrue(top.stream().noneMatch(r -> "w10".equals(r.word())));
        assertEquals("w9", top.get(9).word());
    }

    @Test
    void exactTarget_gameOver_waitingWordsWhenNoPending() throws EmbeddingException {
        GameSemanticRound active = loadAppleRound();
        when(roundRepo.findById(10L)).thenReturn(Optional.of(active));
        int afterLoad = embedCalls.get();

        GameSemanticWord activeWord = new GameSemanticWord();
        activeWord.setId(1L);
        activeWord.setWord("苹果");
        activeWord.setStatus("active");
        when(wordRepo.findById(1L)).thenReturn(Optional.of(activeWord));
        when(wordRepo.findFirstByStatusOrderByQueueOrderAscIdAsc("pending"))
                .thenReturn(Optional.empty());

        GameEngineService.GuessResult result = engine.processGuess("carol", "fp-c", " 苹果 ");
        assertTrue(result.gameOver());
        assertTrue(result.isFirst());
        assertEquals(100.0, result.record().score(), 1e-6);
        assertEquals("waiting_words", result.next());
        assertEquals(afterLoad, embedCalls.get(), "exact match must not embed guess");

        assertEquals("finished", active.getStatus());
        assertEquals("carol", active.getWinnerUsername());
        assertEquals("fp-c", active.getWinnerFp());
        assertTrue(active.getFinishedAt() > 0);

        GameEngineService.RoundSnapshot snap = engine.getRoundSnapshot();
        assertEquals("waiting_words", snap.status());
        assertFalse(snap.guessable());

        verify(wordRepo).save(argThat(w -> "used".equals(w.getStatus())));
        verify(roundRepo, atLeastOnce()).save(argThat(r ->
                "finished".equals(r.getStatus()) || "waiting_words".equals(r.getStatus())));
    }

    @Test
    void exactTarget_withPending_startsNewRound() throws EmbeddingException {
        GameSemanticRound active = loadAppleRound();
        when(roundRepo.findById(10L)).thenReturn(Optional.of(active));

        GameSemanticWord activeWord = new GameSemanticWord();
        activeWord.setId(1L);
        activeWord.setWord("苹果");
        activeWord.setStatus("active");
        when(wordRepo.findById(1L)).thenReturn(Optional.of(activeWord));

        GameSemanticWord pending = new GameSemanticWord();
        pending.setId(2L);
        pending.setWord("香蕉");
        pending.setStatus("pending");
        pending.setQueueOrder(1);
        when(wordRepo.findFirstByStatusOrderByQueueOrderAscIdAsc("pending"))
                .thenReturn(Optional.of(pending));

        GameEngineService.GuessResult result = engine.processGuess("dave", "fp-d", "苹果");
        assertTrue(result.gameOver());
        assertEquals("new_round", result.next());
        assertEquals(100.0, result.record().score(), 1e-6);

        assertEquals("finished", active.getStatus());
        assertEquals("dave", active.getWinnerUsername());
        assertEquals("fp-d", active.getWinnerFp());
        assertTrue(active.getFinishedAt() > 0);

        GameEngineService.RoundSnapshot snap = engine.getRoundSnapshot();
        assertEquals("active", snap.status());
        assertTrue(snap.guessable());
        assertNotEquals(10L, snap.roundId());
        assertTrue(engine.getTop10().isEmpty());

        verify(wordRepo).save(argThat(w -> w.getId().equals(2L) && "active".equals(w.getStatus())));
    }

    @Test
    void exactWin_pendingNextEmbedFails_entersWaitingWords_noOrphanActive() throws EmbeddingException {
        // Embedder: load target ok; next pending target throws
        engine = new GameEngineService(wordRepo, roundRepo, guessRepo, text -> {
            embedCalls.incrementAndGet();
            if ("失败词".equals(text)) {
                throw new EmbeddingException("dashscope down");
            }
            return vectorFor(text);
        });

        GameSemanticRound active = activeRound(10L, "苹果", 1L);
        engine.loadActiveRound(active);
        when(roundRepo.findById(10L)).thenReturn(Optional.of(active));

        GameSemanticWord activeWord = new GameSemanticWord();
        activeWord.setId(1L);
        activeWord.setWord("苹果");
        activeWord.setStatus("active");
        when(wordRepo.findById(1L)).thenReturn(Optional.of(activeWord));

        GameSemanticWord pending = new GameSemanticWord();
        pending.setId(2L);
        pending.setWord("失败词");
        pending.setStatus("pending");
        pending.setQueueOrder(1);
        when(wordRepo.findFirstByStatusOrderByQueueOrderAscIdAsc("pending"))
                .thenReturn(Optional.of(pending));

        GameEngineService.GuessResult result = engine.processGuess("erin", "fp-e", "苹果");
        assertTrue(result.gameOver());
        assertEquals("waiting_words", result.next());

        GameEngineService.RoundSnapshot snap = engine.getRoundSnapshot();
        assertEquals("waiting_words", snap.status());
        assertFalse(snap.guessable());
        assertNotEquals(10L, snap.roundId(), "hot state must leave finished round");
        assertTrue(engine.getTop10().isEmpty());

        verify(wordRepo).save(argThat(w ->
                w.getId().equals(2L) && "skipped".equals(w.getStatus())));
        verify(roundRepo, never()).save(argThat(r ->
                "active".equals(r.getStatus()) && "失败词".equals(r.getTargetWord())));
        verify(roundRepo, atLeastOnce()).save(argThat(r ->
                "waiting_words".equals(r.getStatus())));
    }

    @Test
    void inactiveRound_throwsRoundInactive() {
        engine.clearHotStateWaiting("waiting_words");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> engine.processGuess("eve", "fp-e", "任意"));
        assertTrue(ex.getMessage().contains("ROUND_INACTIVE"));
    }

    @Test
    void inactiveFinished_throwsRoundInactive() {
        GameSemanticRound finished = activeRound(99L, "苹果", 1L);
        finished.setStatus("finished");
        // loadActiveRound only for active; simulate idle/finished via clear
        engine.clearHotStateWaiting("finished");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> engine.processGuess("eve", "fp-e", "苹果"));
        assertTrue(ex.getMessage().contains("ROUND_INACTIVE"));
    }
}
