package com.harvey.digitalgarden.dto;

import lombok.Data;

@Data
public class ProfileRequest {
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String socialLinks;
}
