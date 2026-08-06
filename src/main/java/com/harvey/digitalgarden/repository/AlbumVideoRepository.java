package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.AlbumVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlbumVideoRepository extends JpaRepository<AlbumVideo, Long> {
    List<AlbumVideo> findByPlaceIdOrderBySortOrderAscInDtmAsc(Long placeId);
}
