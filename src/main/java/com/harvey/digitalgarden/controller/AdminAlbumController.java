package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.AlbumPhotoRequest;
import com.harvey.digitalgarden.dto.AlbumPhotoVO;
import com.harvey.digitalgarden.dto.AlbumPlaceDetailVO;
import com.harvey.digitalgarden.dto.AlbumPlaceRequest;
import com.harvey.digitalgarden.dto.AlbumPlaceVO;
import com.harvey.digitalgarden.dto.AlbumVideoRequest;
import com.harvey.digitalgarden.dto.AlbumVideoVO;
import com.harvey.digitalgarden.service.AlbumPlaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/album")
public class AdminAlbumController {

    private final AlbumPlaceService albumPlaceService;

    public AdminAlbumController(AlbumPlaceService albumPlaceService) {
        this.albumPlaceService = albumPlaceService;
    }

    @GetMapping("/places")
    public Result<PageResult<AlbumPlaceVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(albumPlaceService.listAll(page, size));
    }

    @PostMapping("/places")
    public Result<AlbumPlaceDetailVO> create(@Valid @RequestBody AlbumPlaceRequest req) {
        return Result.success(albumPlaceService.create(req));
    }

    @GetMapping("/places/{id}")
    public Result<AlbumPlaceDetailVO> detail(@PathVariable Long id) {
        return Result.success(albumPlaceService.getById(id));
    }

    @PutMapping("/places/{id}")
    public Result<AlbumPlaceDetailVO> update(@PathVariable Long id, @Valid @RequestBody AlbumPlaceRequest req) {
        return Result.success(albumPlaceService.update(id, req));
    }

    @DeleteMapping("/places/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        albumPlaceService.delete(id);
        return Result.success();
    }

    @PostMapping("/places/{id}/photos")
    public Result<AlbumPhotoVO> addPhoto(@PathVariable Long id, @Valid @RequestBody AlbumPhotoRequest req) {
        return Result.success(albumPlaceService.addPhoto(id, req));
    }

    @PutMapping("/photos/{photoId}")
    public Result<AlbumPhotoVO> updatePhoto(@PathVariable Long photoId, @Valid @RequestBody AlbumPhotoRequest req) {
        return Result.success(albumPlaceService.updatePhoto(photoId, req));
    }

    @DeleteMapping("/photos/{photoId}")
    public Result<Void> deletePhoto(@PathVariable Long photoId) {
        albumPlaceService.deletePhoto(photoId);
        return Result.success();
    }

    @PostMapping("/places/{id}/videos")
    public Result<AlbumVideoVO> addVideo(@PathVariable Long id, @Valid @RequestBody AlbumVideoRequest req) {
        return Result.success(albumPlaceService.addVideo(id, req));
    }

    @PutMapping("/videos/{videoId}")
    public Result<AlbumVideoVO> updateVideo(@PathVariable Long videoId, @Valid @RequestBody AlbumVideoRequest req) {
        return Result.success(albumPlaceService.updateVideo(videoId, req));
    }

    @DeleteMapping("/videos/{videoId}")
    public Result<Void> deleteVideo(@PathVariable Long videoId) {
        albumPlaceService.deleteVideo(videoId);
        return Result.success();
    }
}
