package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.GameSemanticGuess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSemanticGuessRepository extends JpaRepository<GameSemanticGuess, Long> {
    List<GameSemanticGuess> findByRoundIdOrderByScoreDescInDtmAsc(Long roundId);

    Optional<GameSemanticGuess> findByRoundIdAndWord(Long roundId, String word);

    long countByRoundId(Long roundId);
}
