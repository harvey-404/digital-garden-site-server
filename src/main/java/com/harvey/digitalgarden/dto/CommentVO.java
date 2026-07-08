package com.harvey.digitalgarden.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long id;
    private Long postId;
    private String nickname;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
