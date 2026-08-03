package com.harvey.digitalgarden.dto;

import lombok.Data;

@Data
public class AlbumPhotoVO {
    private Long id;
    private Long placeId;
    private String fileUrl;
    private String caption;
    private Integer sortOrder;
}
