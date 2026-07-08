package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称过长")
    private String nickname;
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容过长")
    private String content;
}
