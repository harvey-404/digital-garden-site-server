package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.GameSemanticWord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSemanticWordRepository extends JpaRepository<GameSemanticWord, Long> {
    List<GameSemanticWord> findByStatusOrderByQueueOrderAscIdAsc(String status);

    Optional<GameSemanticWord> findFirstByStatusOrderByQueueOrderAscIdAsc(String status);

    long countByStatus(String status);
}
