package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.AlbumPlace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumPlaceRepository extends JpaRepository<AlbumPlace, Long> {
    Page<AlbumPlace> findByStatusOrderBySortOrderAscInDtmDesc(String status, Pageable pageable);
    Page<AlbumPlace> findAllByOrderByInDtmDesc(Pageable pageable);
}
