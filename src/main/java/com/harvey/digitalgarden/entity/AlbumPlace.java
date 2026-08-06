package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "album_place")
@SQLRestriction("is_deleted = 0")
public class AlbumPlace extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name = "";

    @Column(nullable = false, length = 500)
    private String address = "";

    @Column(nullable = false, length = 100)
    private String city = "";

    @Column(nullable = false)
    private Double lat = 0.0;

    @Column(nullable = false)
    private Double lng = 0.0;

    @Column(nullable = false, length = 1000)
    private String note = "";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";
}
