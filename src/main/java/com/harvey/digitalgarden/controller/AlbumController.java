package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.AlbumPlaceDetailVO;
import com.harvey.digitalgarden.dto.AlbumPlaceVO;
import com.harvey.digitalgarden.service.AlbumPlaceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/album")
public class AlbumController {

    private final AlbumPlaceService albumPlaceService;

    public AlbumController(AlbumPlaceService albumPlaceService) {
        this.albumPlaceService = albumPlaceService;
    }

    @GetMapping("/places")
    public Result<PageResult<AlbumPlaceVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(albumPlaceService.listPublished(page, size));
    }

    @GetMapping("/places/{id}")
    public Result<AlbumPlaceDetailVO> detail(@PathVariable Long id) {
        return Result.success(albumPlaceService.getPublishedById(id));
    }
}
