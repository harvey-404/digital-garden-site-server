package com.harvey.digitalgarden.dto;

import lombok.Data;

@Data
public class AlbumPlaceVO {
    private Long id;
    private String name;
    private String address;
    private String city;
    private Double lat;
    private Double lng;
    private String note;
    private Integer sortOrder;
    private String status;
    private String coverUrl;
    private Integer photoCount;
    private Integer videoCount;
    private Long inDtm;
    private Long updateDtm;
}
