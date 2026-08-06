package com.harvey.digitalgarden.game.semantic;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.game.SemanticRoundAdminVO;
import com.harvey.digitalgarden.dto.game.SemanticStatusVO;
import com.harvey.digitalgarden.dto.game.SemanticWordVO;
import com.harvey.digitalgarden.entity.GameSemanticRound;
import com.harvey.digitalgarden.entity.GameSemanticWord;
import com.harvey.digitalgarden.repository.GameSemanticGuessRepository;
import com.harvey.digitalgarden.repository.GameSemanticRoundRepository;
import com.harvey.digitalgarden.repository.GameSemanticWordRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SemanticRoundAdminService {

    private final GameSemanticWordRepository wordRepo;
    private final GameSemanticRoundRepository roundRepo;
    private final GameSemanticGuessRepository guessRepo;
    private final GameEngineService gameEngine;
    private final EmbeddingPort embeddingPort;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public SemanticRoundAdminService(
            GameSemanticWordRepository wordRepo,
            GameSemanticRoundRepository roundRepo,
            GameSemanticGuessRepository guessRepo,
            GameEngineService gameEngine,
            EmbeddingPort embeddingPort,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.wordRepo = wordRepo;
        this.roundRepo = roundRepo;
        this.guessRepo = guessRepo;
        this.gameEngine = gameEngine;
        this.embeddingPort = embeddingPort;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public List<SemanticWordVO> listWords() {
        return wordRepo
                .findAll(Sort.by(Sort.Direction.ASC, "queueOrder").and(Sort.by(Sort.Direction.ASC, "id")))
                .stream()
                .map(this::toWordVO)
                .toList();
    }

    @Transactional
    public List<SemanticWordVO> addWords(List<String> words) {
        if (words == null || words.isEmpty()) {
            throw BusinessException.badRequest("词列表不能为空");
        }
        int nextOrder = nextQueueOrder();
        List<SemanticWordVO> created = new ArrayList<>();
        for (String raw : words) {
            if (raw == null) {
                continue;
            }
            String word = raw.trim();
            if (word.isEmpty()) {
                continue;
            }
            GameSemanticWord entity = new GameSemanticWord();
            entity.setWord(word);
            entity.setQueueOrder(nextOrder++);
            entity.setStatus("pending");
            created.add(toWordVO(wordRepo.save(entity)));
        }
        if (created.isEmpty()) {
            throw BusinessException.badRequest("词列表不能为空");
        }
        return created;
    }

    @Transactional
    public void skipWord(Long id) {
        GameSemanticWord word = wordRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("词不存在"));
        if (!"pending".equals(word.getStatus())) {
            throw BusinessException.badRequest("仅 pending 词可跳过");
        }
        word.setStatus("skipped");
        wordRepo.save(word);
    }

    @Transactional
    public void reorder(Long id, int queueOrder) {
        GameSemanticWord word = wordRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("词不存在"));
        word.setQueueOrder(queueOrder);
        wordRepo.save(word);
    }

    public SemanticRoundAdminVO getAdminStatus() {
        GameEngineService.RoundSnapshot snap = gameEngine.getRoundSnapshot();
        SemanticRoundAdminVO vo = new SemanticRoundAdminVO();
        vo.setStatus(snap.status());
        vo.setRoundId(snap.roundId());
        vo.setGuessable(snap.guessable());
        vo.setPendingCount(wordRepo.countByStatus("pending"));
        vo.setGuessCount(snap.roundId() > 0 ? guessRepo.countByRoundId(snap.roundId()) : 0L);
        vo.setTargetWord("");
        if ("active".equals(snap.status()) && snap.roundId() > 0) {
            roundRepo.findById(snap.roundId()).ifPresent(r ->
                    vo.setTargetWord(r.getTargetWord() == null ? "" : r.getTargetWord()));
        }
        return vo;
    }

    public SemanticStatusVO getPublicStatus() {
        GameEngineService.RoundSnapshot snap = gameEngine.getRoundSnapshot();
        SemanticStatusVO vo = new SemanticStatusVO();
        vo.setStatus(snap.status());
        vo.setRoundId(snap.roundId());
        vo.setGuessable(snap.guessable());
        return vo;
    }

    /**
     * Embed target before opening a short DB transaction (avoids holding JDBC across DashScope
     * and orphaned active rounds when embed fails).
     */
    public SemanticRoundAdminVO startRound() {
        GameEngineService.RoundSnapshot snap = gameEngine.getRoundSnapshot();
        if ("active".equals(snap.status())) {
            throw BusinessException.conflict("当前已有进行中的轮次");
        }

        long now = Instant.now().getEpochSecond();
        Optional<GameSemanticWord> pending =
                wordRepo.findFirstByStatusOrderByQueueOrderAscIdAsc("pending");
        if (pending.isEmpty()) {
            ensureWaitingWords(now);
            publishRoundSnapshotChanged();
            return getAdminStatus();
        }

        GameSemanticWord word = pending.get();
        String target = word.getWord() == null ? "" : word.getWord();
        float[] embedding;
        try {
            embedding = embeddingPort.embed(target);
        } catch (EmbeddingException e) {
            tx.executeWithoutResult(status -> {
                word.setStatus("skipped");
                wordRepo.save(word);
            });
            ensureWaitingWords(now);
            publishRoundSnapshotChanged();
            throw BusinessException.badRequest("目标词向量化失败，已跳过该词并进入等待词库");
        }

        GameSemanticRound round = tx.execute(status -> {
            finishOpenWaitingRound(now);
            word.setStatus("active");
            wordRepo.save(word);

            GameSemanticRound r = new GameSemanticRound();
            r.setTargetWord(target);
            r.setWordId(word.getId());
            r.setStatus("active");
            r.setStartedAt(now);
            r.setFinishedAt(0L);
            r.setWinnerUsername("");
            r.setWinnerFp("");
            return roundRepo.save(r);
        });
        if (round == null || round.getId() == null) {
            throw BusinessException.badRequest("开局失败：未能创建轮次");
        }

        gameEngine.loadActiveRound(round, embedding);
        publishRoundSnapshotChanged();
        return getAdminStatus();
    }

    public SemanticRoundAdminVO stopRound() {
        GameEngineService.RoundSnapshot snap = gameEngine.getRoundSnapshot();
        if (!"active".equals(snap.status()) || snap.roundId() <= 0) {
            throw BusinessException.badRequest("当前没有进行中的轮次");
        }

        long now = Instant.now().getEpochSecond();
        long roundId = snap.roundId();
        tx.executeWithoutResult(status -> {
            GameSemanticRound round = roundRepo.findById(roundId)
                    .orElseThrow(() -> BusinessException.notFound("轮次不存在"));
            round.setStatus("finished");
            round.setFinishedAt(now);
            roundRepo.save(round);

            if (round.getWordId() != null && round.getWordId() > 0) {
                wordRepo.findById(round.getWordId()).ifPresent(w -> {
                    if ("active".equals(w.getStatus())) {
                        w.setStatus("used");
                        wordRepo.save(w);
                    }
                });
            }
        });
        gameEngine.clearHotStateWaiting("finished");
        publishRoundSnapshotChanged();
        return getAdminStatus();
    }

    private void publishRoundSnapshotChanged() {
        events.publishEvent(new RoundSnapshotChangedEvent());
    }

    private void ensureWaitingWords(long now) {
        tx.executeWithoutResult(status -> {
            Optional<GameSemanticRound> open =
                    roundRepo.findFirstByStatusInOrderByIdDesc(List.of("active", "waiting_words"));
            GameSemanticRound waiting;
            if (open.isPresent() && "waiting_words".equals(open.get().getStatus())) {
                waiting = open.get();
            } else {
                if (open.isPresent() && "active".equals(open.get().getStatus())) {
                    GameSemanticRound active = open.get();
                    active.setStatus("finished");
                    active.setFinishedAt(now);
                    roundRepo.save(active);
                }
                waiting = new GameSemanticRound();
                waiting.setTargetWord("");
                waiting.setWordId(0L);
                waiting.setStatus("waiting_words");
                waiting.setStartedAt(now);
                waiting.setFinishedAt(0L);
                waiting.setWinnerUsername("");
                waiting.setWinnerFp("");
                roundRepo.save(waiting);
            }
            gameEngine.enterWaitingWords(waiting.getId() == null ? 0L : waiting.getId());
        });
    }

    private void finishOpenWaitingRound(long now) {
        roundRepo.findFirstByStatusInOrderByIdDesc(List.of("waiting_words")).ifPresent(w -> {
            if ("waiting_words".equals(w.getStatus())) {
                w.setStatus("finished");
                w.setFinishedAt(now);
                roundRepo.save(w);
            }
        });
    }

    private int nextQueueOrder() {
        return wordRepo
                .findAll(Sort.by(Sort.Direction.DESC, "queueOrder").and(Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .findFirst()
                .map(w -> (w.getQueueOrder() == null ? 0 : w.getQueueOrder()) + 1)
                .orElse(0);
    }

    private SemanticWordVO toWordVO(GameSemanticWord word) {
        SemanticWordVO vo = new SemanticWordVO();
        vo.setId(word.getId());
        vo.setWord(word.getWord());
        vo.setQueueOrder(word.getQueueOrder());
        vo.setStatus(word.getStatus());
        return vo;
    }
}
