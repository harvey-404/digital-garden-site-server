package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "album_photo")
@SQLRestriction("is_deleted = 0")
public class AlbumPhoto extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId = 0L;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl = "";

    @Column(nullable = false, length = 500)
    private String caption = "";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
