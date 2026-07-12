package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PostTagRepository extends JpaRepository<PostTag, Long> {
    List<PostTag> findByPostId(Long postId);

    Optional<PostTag> findByPostIdAndTagId(Long postId, Long tagId);

    @Modifying
    @Query("update PostTag pt set pt.isDeleted = true, pt.updateDtm = :now where pt.postId = :postId and pt.isDeleted = false")
    void softDeleteByPostId(@Param("postId") Long postId, @Param("now") Long now);

    @Modifying
    @Query(value = "UPDATE post_tag SET is_deleted = 0, update_dtm = :now WHERE post_id = :postId AND tag_id = :tagId AND is_deleted = 1",
            nativeQuery = true)
    int restoreByPostIdAndTagId(@Param("postId") Long postId, @Param("tagId") Long tagId, @Param("now") Long now);
}
