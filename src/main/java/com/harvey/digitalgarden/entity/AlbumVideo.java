package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "album_video")
@SQLRestriction("is_deleted = 0")
public class AlbumVideo extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId = 0L;

    @Column(nullable = false, length = 1000)
    private String url = "";

    @Column(nullable = false, length = 50)
    private String platform = "other";

    @Column(nullable = false, length = 200)
    private String title = "";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
