package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.dto.AlbumPhotoRequest;
import com.harvey.digitalgarden.dto.AlbumPhotoVO;
import com.harvey.digitalgarden.dto.AlbumPlaceDetailVO;
import com.harvey.digitalgarden.dto.AlbumPlaceRequest;
import com.harvey.digitalgarden.dto.AlbumPlaceVO;
import com.harvey.digitalgarden.dto.AlbumVideoRequest;
import com.harvey.digitalgarden.dto.AlbumVideoVO;
import com.harvey.digitalgarden.entity.AlbumPhoto;
import com.harvey.digitalgarden.entity.AlbumPlace;
import com.harvey.digitalgarden.entity.AlbumVideo;
import com.harvey.digitalgarden.repository.AlbumPhotoRepository;
import com.harvey.digitalgarden.repository.AlbumPlaceRepository;
import com.harvey.digitalgarden.repository.AlbumVideoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class AlbumPlaceService {

    private static final double CHINA_LAT_MIN = 3.0;
    private static final double CHINA_LAT_MAX = 54.0;
    private static final double CHINA_LNG_MIN = 73.0;
    private static final double CHINA_LNG_MAX = 135.0;

    private final AlbumPlaceRepository albumPlaceRepository;
    private final AlbumPhotoRepository albumPhotoRepository;
    private final AlbumVideoRepository albumVideoRepository;

    public AlbumPlaceService(
            AlbumPlaceRepository albumPlaceRepository,
            AlbumPhotoRepository albumPhotoRepository,
            AlbumVideoRepository albumVideoRepository) {
        this.albumPlaceRepository = albumPlaceRepository;
        this.albumPhotoRepository = albumPhotoRepository;
        this.albumVideoRepository = albumVideoRepository;
    }

    public PageResult<AlbumPlaceVO> listPublished(int page, int size) {
        Page<AlbumPlace> result = albumPlaceRepository.findByStatusOrderBySortOrderAscInDtmDesc(
                "PUBLISHED", PageRequest.of(page, size));
        List<AlbumPlaceVO> items = result.getContent().stream().map(this::toVO).toList();
        return PageResult.of(result, items);
    }

    public AlbumPlaceDetailVO getPublishedById(Long id) {
        AlbumPlace place = albumPlaceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("地点不存在"));
        if (!"PUBLISHED".equals(place.getStatus())) {
            throw BusinessException.notFound("地点不存在");
        }
        return toDetailVO(place);
    }

    public PageResult<AlbumPlaceVO> listAll(int page, int size) {
        Page<AlbumPlace> result = albumPlaceRepository.findAllByOrderByInDtmDesc(PageRequest.of(page, size));
        List<AlbumPlaceVO> items = result.getContent().stream().map(this::toVO).toList();
        return PageResult.of(result, items);
    }

    public AlbumPlaceDetailVO getById(Long id) {
        AlbumPlace place = albumPlaceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("地点不存在"));
        return toDetailVO(place);
    }

    @Transactional
    public AlbumPlaceDetailVO create(AlbumPlaceRequest req) {
        assertInChina(req.getLat(), req.getLng());
        AlbumPlace place = new AlbumPlace();
        apply(place, req);
        return toDetailVO(albumPlaceRepository.save(place));
    }

    @Transactional
    public AlbumPlaceDetailVO update(Long id, AlbumPlaceRequest req) {
        AlbumPlace place = albumPlaceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("地点不存在"));
        assertInChina(req.getLat(), req.getLng());
        apply(place, req);
        return toDetailVO(albumPlaceRepository.save(place));
    }

    @Transactional
    public void delete(Long id) {
        AlbumPlace place = albumPlaceRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("地点不存在"));
        long now = Instant.now().getEpochSecond();
        place.setIsDeleted(true);
        place.setUpdateDtm(now);
        albumPlaceRepository.save(place);
        for (AlbumPhoto photo : albumPhotoRepository.findByPlaceIdOrderBySortOrderAscInDtmAsc(id)) {
            photo.setIsDeleted(true);
            photo.setUpdateDtm(now);
            albumPhotoRepository.save(photo);
        }
        for (AlbumVideo video : albumVideoRepository.findByPlaceIdOrderBySortOrderAscInDtmAsc(id)) {
            video.setIsDeleted(true);
            video.setUpdateDtm(now);
            albumVideoRepository.save(video);
        }
    }

    @Transactional
    public AlbumPhotoVO addPhoto(Long placeId, AlbumPhotoRequest req) {
        ensurePlaceExists(placeId);
        AlbumPhoto photo = new AlbumPhoto();
        photo.setPlaceId(placeId);
        photo.setFileUrl(req.getFileUrl());
        photo.setCaption(req.getCaption() == null ? "" : req.getCaption());
        photo.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return toPhotoVO(albumPhotoRepository.save(photo));
    }

    @Transactional
    public AlbumPhotoVO updatePhoto(Long photoId, AlbumPhotoRequest req) {
        AlbumPhoto photo = albumPhotoRepository.findById(photoId)
                .orElseThrow(() -> BusinessException.notFound("照片不存在"));
        photo.setFileUrl(req.getFileUrl());
        photo.setCaption(req.getCaption() == null ? "" : req.getCaption());
        photo.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return toPhotoVO(albumPhotoRepository.save(photo));
    }

    @Transactional
    public void deletePhoto(Long photoId) {
        AlbumPhoto photo = albumPhotoRepository.findById(photoId)
                .orElseThrow(() -> BusinessException.notFound("照片不存在"));
        photo.setIsDeleted(true);
        photo.setUpdateDtm(Instant.now().getEpochSecond());
        albumPhotoRepository.save(photo);
    }

    @Transactional
    public AlbumVideoVO addVideo(Long placeId, AlbumVideoRequest req) {
        ensurePlaceExists(placeId);
        assertHttpUrl(req.getUrl());
        AlbumVideo video = new AlbumVideo();
        video.setPlaceId(placeId);
        video.setUrl(req.getUrl());
        video.setPlatform(req.getPlatform() == null ? "other" : req.getPlatform());
        video.setTitle(req.getTitle() == null ? "" : req.getTitle());
        video.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return toVideoVO(albumVideoRepository.save(video));
    }

    @Transactional
    public AlbumVideoVO updateVideo(Long videoId, AlbumVideoRequest req) {
        assertHttpUrl(req.getUrl());
        AlbumVideo video = albumVideoRepository.findById(videoId)
                .orElseThrow(() -> BusinessException.notFound("视频不存在"));
        video.setUrl(req.getUrl());
        video.setPlatform(req.getPlatform() == null ? "other" : req.getPlatform());
        video.setTitle(req.getTitle() == null ? "" : req.getTitle());
        video.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        return toVideoVO(albumVideoRepository.save(video));
    }

    @Transactional
    public void deleteVideo(Long videoId) {
        AlbumVideo video = albumVideoRepository.findById(videoId)
                .orElseThrow(() -> BusinessException.notFound("视频不存在"));
        video.setIsDeleted(true);
        video.setUpdateDtm(Instant.now().getEpochSecond());
        albumVideoRepository.save(video);
    }

    private void ensurePlaceExists(Long placeId) {
        albumPlaceRepository.findById(placeId)
                .orElseThrow(() -> BusinessException.notFound("地点不存在"));
    }

    private void assertHttpUrl(String url) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw BusinessException.badRequest("视频链接须以 http:// 或 https:// 开头");
        }
    }

    private void apply(AlbumPlace place, AlbumPlaceRequest req) {
        place.setName(req.getName());
        place.setAddress(req.getAddress() == null ? "" : req.getAddress());
        place.setCity(req.getCity() == null ? "" : req.getCity());
        place.setLat(req.getLat());
        place.setLng(req.getLng());
        place.setNote(req.getNote() == null ? "" : req.getNote());
        place.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        place.setStatus(normalizeStatus(req.getStatus()));
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "DRAFT";
        }
        return "PUBLISHED".equalsIgnoreCase(status) ? "PUBLISHED" : "DRAFT";
    }

    private void assertInChina(Double lat, Double lng) {
        if (lat == null || lng == null
                || lat < CHINA_LAT_MIN || lat > CHINA_LAT_MAX
                || lng < CHINA_LNG_MIN || lng > CHINA_LNG_MAX) {
            throw BusinessException.badRequest("坐标需在中国范围内");
        }
    }

    private AlbumPlaceVO toVO(AlbumPlace place) {
        List<AlbumPhoto> photos = albumPhotoRepository.findByPlaceIdOrderBySortOrderAscInDtmAsc(place.getId());
        List<AlbumVideo> videos = albumVideoRepository.findByPlaceIdOrderBySortOrderAscInDtmAsc(place.getId());
        return toVO(place, photos, videos);
    }

    private AlbumPlaceVO toVO(AlbumPlace place, List<AlbumPhoto> photos, List<AlbumVideo> videos) {
        AlbumPlaceVO vo = new AlbumPlaceVO();
        vo.setId(place.getId());
        vo.setName(place.getName());
        vo.setAddress(place.getAddress());
        vo.setCity(place.getCity());
        vo.setLat(place.getLat());
        vo.setLng(place.getLng());
        vo.setNote(place.getNote());
        vo.setSortOrder(place.getSortOrder());
        vo.setStatus(place.getStatus());
        vo.setCoverUrl(photos.isEmpty() ? "" : photos.get(0).getFileUrl());
        vo.setPhotoCount(photos.size());
        vo.setVideoCount(videos.size());
        vo.setInDtm(place.getInDtm());
        vo.setUpdateDtm(place.getUpdateDtm());
        return vo;
    }

    private AlbumPlaceDetailVO toDetailVO(AlbumPlace place) {
        List<AlbumPhoto> photos = albumPhotoRepository.findByPlaceIdOrderBySortOrderAscInDtmAsc(place.getId());
        List<AlbumVideo> videos = albumVideoRepository.findByPlaceIdOrderBySortOrderAscInDtmAsc(place.getId());

        AlbumPlaceDetailVO vo = new AlbumPlaceDetailVO();
        AlbumPlaceVO base = toVO(place, photos, videos);
        vo.setId(base.getId());
        vo.setName(base.getName());
        vo.setAddress(base.getAddress());
        vo.setCity(base.getCity());
        vo.setLat(base.getLat());
        vo.setLng(base.getLng());
        vo.setNote(base.getNote());
        vo.setSortOrder(base.getSortOrder());
        vo.setStatus(base.getStatus());
        vo.setCoverUrl(base.getCoverUrl());
        vo.setPhotoCount(base.getPhotoCount());
        vo.setVideoCount(base.getVideoCount());
        vo.setInDtm(base.getInDtm());
        vo.setUpdateDtm(base.getUpdateDtm());
        vo.setPhotos(photos.stream().map(this::toPhotoVO).toList());
        vo.setVideos(videos.stream().map(this::toVideoVO).toList());
        return vo;
    }

    private AlbumPhotoVO toPhotoVO(AlbumPhoto photo) {
        AlbumPhotoVO vo = new AlbumPhotoVO();
        vo.setId(photo.getId());
        vo.setPlaceId(photo.getPlaceId());
        vo.setFileUrl(photo.getFileUrl());
        vo.setCaption(photo.getCaption());
        vo.setSortOrder(photo.getSortOrder());
        return vo;
    }

    private AlbumVideoVO toVideoVO(AlbumVideo video) {
        AlbumVideoVO vo = new AlbumVideoVO();
        vo.setId(video.getId());
        vo.setPlaceId(video.getPlaceId());
        vo.setUrl(video.getUrl());
        vo.setPlatform(video.getPlatform());
        vo.setTitle(video.getTitle());
        vo.setSortOrder(video.getSortOrder());
        return vo;
    }
}
