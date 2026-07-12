package com.harvey.digitalgarden.dto;

import lombok.Data;

@Data
public class TodoVO {
    private Long id;
    private String title;
    private String slug;
    private String description;
    private String priority;
    private Integer progress;
    private String status;
    private Integer sortOrder;
    private Long inDtm;
}
