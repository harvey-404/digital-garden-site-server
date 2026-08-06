package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.GameSemanticRound;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSemanticRoundRepository extends JpaRepository<GameSemanticRound, Long> {
    Optional<GameSemanticRound> findFirstByStatusInOrderByIdDesc(Collection<String> statuses);
}
