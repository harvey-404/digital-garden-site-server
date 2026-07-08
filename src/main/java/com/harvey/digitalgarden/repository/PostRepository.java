package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findBySlug(String slug);
    boolean existsBySlug(String slug);

    Page<Post> findByStatus(String status, Pageable pageable);

    @Query("select distinct p from Post p join p.tags t " +
            "where p.status = 'PUBLISHED' and t.name = :tagName")
    Page<Post> findPublishedByTag(@Param("tagName") String tagName, Pageable pageable);

    @Query("select p from Post p where p.status = 'PUBLISHED' and " +
            "(lower(p.title) like lower(concat('%', :q, '%')) " +
            "or lower(p.contentMd) like lower(concat('%', :q, '%')))")
    Page<Post> searchPublished(@Param("q") String q, Pageable pageable);
}
