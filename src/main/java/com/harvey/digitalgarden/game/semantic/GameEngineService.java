package com.harvey.digitalgarden.game.semantic;

import com.harvey.digitalgarden.entity.GameSemanticGuess;
import com.harvey.digitalgarden.entity.GameSemanticRound;
import com.harvey.digitalgarden.entity.GameSemanticWord;
import com.harvey.digitalgarden.repository.GameSemanticGuessRepository;
import com.harvey.digitalgarden.repository.GameSemanticRoundRepository;
import com.harvey.digitalgarden.repository.GameSemanticWordRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hot-state semantic guessing engine. WS/admin callers must catch {@link EmbeddingException}
 * and must not persist guess/round side effects when embedding fails.
 */
@Service
public class GameEngineService {

    private static final Logger log = LoggerFactory.getLogger(GameEngineService.class);

    public record WordRecord(String word, double score, String firstUser, String firstFp) {}

    public record GuessResult(
            WordRecord record,
            boolean isFirst,
            boolean alreadyGuessed,
            boolean needBroadcastTop10,
            boolean gameOver,
            String next, // "" | "new_round" | "waiting_words"
            boolean hintsUpdated
    ) {}

    public record RoundSnapshot(long roundId, String status, boolean guessable) {}

    private static final int MAX_WORD_LEN = 20;
    private static final int TOP_N = 10;
    private static final int HINT_EVERY_N = 10;
    private static final int MAX_HINTS = 3;

    private final GameSemanticWordRepository wordRepo;
    private final GameSemanticRoundRepository roundRepo;
    private final GameSemanticGuessRepository guessRepo;
    private final EmbeddingPort embeddingPort;

    private volatile long roundId;
    private volatile long wordId;
    private volatile String status = "idle";
    private volatile String targetWord = "";
    private volatile float[] targetEmbedding;
    private volatile String hint1 = "";
    private volatile String hint2 = "";
    private volatile String hint3 = "";
    private final ConcurrentHashMap<String, WordRecord> discovered = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<WordRecord> top10 = new CopyOnWriteArrayList<>();

    public GameEngineService(
            GameSemanticWordRepository wordRepo,
            GameSemanticRoundRepository roundRepo,
            GameSemanticGuessRepository guessRepo,
            EmbeddingPort embeddingPort) {
        this.wordRepo = wordRepo;
        this.roundRepo = roundRepo;
        this.guessRepo = guessRepo;
        this.embeddingPort = embeddingPort;
    }

    public RoundSnapshot getRoundSnapshot() {
        return new RoundSnapshot(roundId, status, "active".equals(status));
    }

    public List<WordRecord> getTop10() {
        return List.copyOf(top10);
    }

    /** Unlocked progressive hints for the current round (empty slots omitted). */
    public List<String> getUnlockedHints() {
        if (!"active".equals(status)) {
            return List.of();
        }
        int slots = hintUnlockLevel(discovered.size());
        return unlockedHintsForSlots(slots);
    }

    public int getHintUnlockLevel() {
        if (!"active".equals(status)) {
            return 0;
        }
        return hintUnlockLevel(discovered.size());
    }

    /** Reload hint texts for the active word without re-embedding. */
    public synchronized boolean refreshHintsIfCurrentWord(Long id) {
        if (id == null || id <= 0 || this.wordId != id) {
            return false;
        }
        loadHintsForWord(this.wordId);
        return true;
    }

    public synchronized GuessResult processGuess(String username, String fp, String rawWord)
            throws EmbeddingException {
        if (!"active".equals(status)) {
            throw new IllegalStateException("ROUND_INACTIVE");
        }
        String word = normalize(rawWord);

        WordRecord existing = discovered.get(word);
        if (existing != null) {
            return new GuessResult(existing, false, true, false, false, "", false);
        }

        int unlockBefore = hintUnlockLevel(discovered.size());

        double score;
        boolean exact = word.equals(targetWord);
        if (exact) {
            score = 100.0;
        } else {
            float[] guessVec = embeddingPort.embed(word);
            double cos = SimilarityMapper.cosine(guessVec, targetEmbedding);
            score = SimilarityMapper.toScore(cos);
        }

        WordRecord record = new WordRecord(word, score, username, fp);
        discovered.put(word, record);
        boolean needBroadcast = insertTop10(record);
        persistGuess(record);

        boolean hintsUpdated = hintUnlockLevel(discovered.size()) > unlockBefore;

        if (!exact) {
            return new GuessResult(record, true, false, needBroadcast, false, "", hintsUpdated);
        }

        String next = finishRoundAndAdvance(username, fp);
        return new GuessResult(record, true, false, needBroadcast, true, next, true);
    }

    @PostConstruct
    public void restoreFromDb() {
        Optional<GameSemanticRound> current = roundRepo.findFirstByStatusInOrderByIdDesc(
                List.of("active", "waiting_words"));
        if (current.isEmpty()) {
            clearHotStateWaiting("idle");
            return;
        }
        GameSemanticRound round = current.get();
        if ("active".equals(round.getStatus())) {
            loadActiveRound(round);
        } else {
            this.roundId = round.getId() == null ? 0L : round.getId();
            this.wordId = 0L;
            this.status = "waiting_words";
            this.targetWord = "";
            this.targetEmbedding = null;
            this.hint1 = "";
            this.hint2 = "";
            this.hint3 = "";
            discovered.clear();
            top10.clear();
        }
    }

    /** Embed target (if needed) then apply hot state. Never mutates hot state if embed fails. */
    public synchronized void loadActiveRound(GameSemanticRound round) {
        loadActiveRound(round, null);
    }

    /**
     * Apply an active round into hot state. When {@code precomputedEmbedding} is non-null,
     * skips re-embed (used after auto-advance / admin start already embedded successfully).
     */
    public synchronized void loadActiveRound(GameSemanticRound round, float[] precomputedEmbedding) {
        if (round == null || round.getId() == null) {
            throw new IllegalArgumentException("round required");
        }
        String nextStatus = round.getStatus() == null ? "active" : round.getStatus();
        String nextTarget = round.getTargetWord() == null ? "" : round.getTargetWord();
        float[] nextEmbedding = precomputedEmbedding;
        if (nextEmbedding == null && "active".equals(nextStatus) && !nextTarget.isBlank()) {
            try {
                nextEmbedding = embeddingPort.embed(nextTarget);
            } catch (EmbeddingException e) {
                throw new IllegalStateException("TARGET_EMBED_FAILED", e);
            }
        }
        applyLoadedRound(round, nextEmbedding);
    }

    /** Mutate hot state from an already-persisted round + embedding (may be null for non-active). */
    private void applyLoadedRound(GameSemanticRound round, float[] embedding) {
        this.roundId = round.getId();
        this.wordId = round.getWordId() == null ? 0L : round.getWordId();
        this.status = round.getStatus() == null ? "active" : round.getStatus();
        this.targetWord = round.getTargetWord() == null ? "" : round.getTargetWord();
        this.targetEmbedding = embedding;
        loadHintsForWord(this.wordId);
        discovered.clear();
        top10.clear();

        List<GameSemanticGuess> guesses =
                guessRepo.findByRoundIdOrderByScoreDescInDtmAsc(this.roundId);
        List<WordRecord> rebuilt = new ArrayList<>();
        for (GameSemanticGuess g : guesses) {
            WordRecord rec = new WordRecord(
                    g.getWord(),
                    g.getScore() == null ? 0.0 : g.getScore(),
                    g.getFirstUsername(),
                    g.getFirstFp());
            discovered.put(rec.word(), rec);
            rebuilt.add(rec);
        }
        rebuilt.sort((a, b) -> Double.compare(b.score(), a.score()));
        top10.addAll(rebuilt.stream().limit(TOP_N).toList());
    }

    private void loadHintsForWord(long id) {
        this.hint1 = "";
        this.hint2 = "";
        this.hint3 = "";
        if (id <= 0) {
            return;
        }
        wordRepo.findById(id).ifPresent(w -> {
            this.hint1 = w.getHint1() == null ? "" : w.getHint1().trim();
            this.hint2 = w.getHint2() == null ? "" : w.getHint2().trim();
            this.hint3 = w.getHint3() == null ? "" : w.getHint3().trim();
        });
    }

    public synchronized void clearHotStateWaiting(String status) {
        this.status = status == null ? "idle" : status;
        this.wordId = 0L;
        this.targetWord = "";
        this.targetEmbedding = null;
        this.hint1 = "";
        this.hint2 = "";
        this.hint3 = "";
        discovered.clear();
        top10.clear();
        if (!"waiting_words".equals(this.status) && !"active".equals(this.status)) {
            this.roundId = 0L;
        }
    }

    /** Enter waiting_words hot state with an explicit round id (admin / no-pending start). */
    public synchronized void enterWaitingWords(long waitingRoundId) {
        this.roundId = waitingRoundId;
        clearHotStateWaiting("waiting_words");
        this.roundId = waitingRoundId;
    }

    private String normalize(String rawWord) {
        if (rawWord == null) {
            throw new IllegalArgumentException("EMPTY_WORD");
        }
        String word = rawWord.trim();
        if (word.isEmpty()) {
            throw new IllegalArgumentException("EMPTY_WORD");
        }
        if (word.length() > MAX_WORD_LEN) {
            throw new IllegalArgumentException("WORD_TOO_LONG");
        }
        return word;
    }

    private boolean insertTop10(WordRecord record) {
        List<WordRecord> next = new ArrayList<>(top10);
        next.add(record);
        next.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (next.size() > TOP_N) {
            next = new ArrayList<>(next.subList(0, TOP_N));
        }
        boolean changed = !next.equals(new ArrayList<>(top10));
        if (changed) {
            top10.clear();
            top10.addAll(next);
        }
        return next.contains(record);
    }

    private void persistGuess(WordRecord record) {
        GameSemanticGuess guess = new GameSemanticGuess();
        guess.setRoundId(roundId);
        guess.setWord(record.word());
        guess.setScore(record.score());
        guess.setFirstUsername(record.firstUser());
        guess.setFirstFp(record.firstFp());
        guessRepo.save(guess);
    }

    /**
     * Finish current round, then either start next (embed-before-persist) or waiting_words.
     * On next-target embed failure: mark word skipped, enter waiting_words — never leave an
     * orphaned active round in DB without matching hot state.
     */
    private String finishRoundAndAdvance(String winnerUsername, String winnerFp) {
        long now = Instant.now().getEpochSecond();
        long finishedRoundId = this.roundId;
        long finishedWordId = this.wordId;

        GameSemanticRound finished = roundRepo.findById(finishedRoundId).orElseThrow(() -> {
            log.error("finishRoundAndAdvance: active round missing from DB, roundId={}", finishedRoundId);
            return new IllegalStateException("ROUND_MISSING:" + finishedRoundId);
        });
        finished.setStatus("finished");
        finished.setWinnerUsername(winnerUsername == null ? "" : winnerUsername);
        finished.setWinnerFp(winnerFp == null ? "" : winnerFp);
        finished.setFinishedAt(now);
        roundRepo.save(finished);

        if (finishedWordId > 0) {
            wordRepo.findById(finishedWordId).ifPresent(w -> {
                w.setStatus("used");
                wordRepo.save(w);
            });
        }

        Optional<GameSemanticWord> pending =
                wordRepo.findFirstByStatusOrderByQueueOrderAscIdAsc("pending");
        if (pending.isPresent()) {
            GameSemanticWord nextWord = pending.get();
            String nextTarget = nextWord.getWord() == null ? "" : nextWord.getWord();
            float[] nextEmbedding;
            try {
                nextEmbedding = embeddingPort.embed(nextTarget);
            } catch (EmbeddingException e) {
                log.warn("finishRoundAndAdvance: next target embed failed, wordId={} word={}",
                        nextWord.getId(), nextTarget, e);
                nextWord.setStatus("skipped");
                wordRepo.save(nextWord);
                persistAndEnterWaitingWords(now);
                return "waiting_words";
            }

            nextWord.setStatus("active");
            wordRepo.save(nextWord);

            GameSemanticRound nextRound = new GameSemanticRound();
            nextRound.setTargetWord(nextTarget);
            nextRound.setWordId(nextWord.getId());
            nextRound.setStatus("active");
            nextRound.setStartedAt(now);
            nextRound.setFinishedAt(0L);
            nextRound.setWinnerUsername("");
            nextRound.setWinnerFp("");
            roundRepo.save(nextRound);

            loadActiveRound(nextRound, nextEmbedding);
            return "new_round";
        }

        persistAndEnterWaitingWords(now);
        return "waiting_words";
    }

    private void persistAndEnterWaitingWords(long now) {
        GameSemanticRound waiting = new GameSemanticRound();
        waiting.setTargetWord("");
        waiting.setWordId(0L);
        waiting.setStatus("waiting_words");
        waiting.setStartedAt(now);
        waiting.setFinishedAt(0L);
        waiting.setWinnerUsername("");
        waiting.setWinnerFp("");
        roundRepo.save(waiting);

        long waitingId = waiting.getId() == null ? 0L : waiting.getId();
        enterWaitingWords(waitingId);
    }

    private static int hintUnlockLevel(int uniqueGuessCount) {
        return Math.min(MAX_HINTS, Math.max(0, uniqueGuessCount) / HINT_EVERY_N);
    }

    private List<String> unlockedHintsForSlots(int slots) {
        String[] bank = {hint1, hint2, hint3};
        List<String> out = new ArrayList<>();
        for (int i = 0; i < slots && i < bank.length; i++) {
            if (bank[i] != null && !bank[i].isBlank()) {
                out.add(bank[i]);
            }
        }
        return List.copyOf(out);
    }
}
