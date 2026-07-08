package com.harvey.digitalgarden.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LikeResponse {
    private Integer likeCount;
    private boolean liked;
}
