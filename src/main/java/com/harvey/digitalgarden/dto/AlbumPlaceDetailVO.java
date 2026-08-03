package com.harvey.digitalgarden.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AlbumPlaceDetailVO extends AlbumPlaceVO {
    private List<AlbumPhotoVO> photos;
    private List<AlbumVideoVO> videos;
}
