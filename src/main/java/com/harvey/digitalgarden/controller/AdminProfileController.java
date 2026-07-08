package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ProfileRequest;
import com.harvey.digitalgarden.dto.ProfileVO;
import com.harvey.digitalgarden.service.ProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/profile")
public class AdminProfileController {

    private final ProfileService profileService;

    public AdminProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping
    public Result<ProfileVO> update(@RequestBody ProfileRequest req) {
        return Result.success(profileService.update(req));
    }
}
