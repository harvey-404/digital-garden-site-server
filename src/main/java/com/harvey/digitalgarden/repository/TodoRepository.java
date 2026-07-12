package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    Optional<Todo> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query(value = """
            SELECT * FROM todo t
            WHERE t.is_deleted = 0 AND t.status = 'PUBLISHED'
            AND (:priority IS NULL OR :priority = '' OR t.priority = :priority)
            AND t.progress >= :minProgress AND t.progress <= :maxProgress
            ORDER BY FIELD(t.priority, 'HIGH', 'MEDIUM', 'LOW'), t.sort_order ASC, t.in_dtm DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM todo t
            WHERE t.is_deleted = 0 AND t.status = 'PUBLISHED'
            AND (:priority IS NULL OR :priority = '' OR t.priority = :priority)
            AND t.progress >= :minProgress AND t.progress <= :maxProgress
            """,
            nativeQuery = true)
    Page<Todo> findPublishedFiltered(
            @Param("priority") String priority,
            @Param("minProgress") int minProgress,
            @Param("maxProgress") int maxProgress,
            Pageable pageable);
}
