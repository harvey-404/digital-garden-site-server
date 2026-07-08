package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ProfileVO;
import com.harvey.digitalgarden.service.ProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public Result<ProfileVO> get() {
        return Result.success(profileService.get());
    }
}
