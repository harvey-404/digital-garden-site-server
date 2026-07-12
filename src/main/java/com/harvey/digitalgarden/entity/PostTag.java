package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "post_tag", uniqueConstraints = @UniqueConstraint(name = "uq_post_tag_pair", columnNames = {"post_id", "tag_id"}))
@SQLRestriction("is_deleted = 0")
public class PostTag extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId = 0L;

    @Column(name = "tag_id", nullable = false)
    private Long tagId = 0L;
}
