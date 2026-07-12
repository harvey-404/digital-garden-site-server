package com.harvey.digitalgarden.dto;

import lombok.Data;
import java.util.List;

@Data
public class PostVO {
    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String coverImage;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private List<String> tags;
    private Long inDtm;
}
