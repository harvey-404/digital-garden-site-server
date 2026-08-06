package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.AlbumPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlbumPhotoRepository extends JpaRepository<AlbumPhoto, Long> {
    List<AlbumPhoto> findByPlaceIdOrderBySortOrderAscInDtmAsc(Long placeId);
}
