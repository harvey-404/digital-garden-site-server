package com.harvey.digitalgarden.dto;

import lombok.Data;

@Data
public class AlbumVideoVO {
    private Long id;
    private Long placeId;
    private String url;
    private String platform;
    private String title;
    private Integer sortOrder;
}
