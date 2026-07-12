package com.harvey.digitalgarden.dto;

import lombok.Data;
import java.util.List;

@Data
public class PostDetailVO {
    private Long id;
    private String title;
    private String slug;
    private String contentMd;
    private String summary;
    private String coverImage;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private List<String> tags;
    private Long inDtm;
    private Long updateDtm;
}
