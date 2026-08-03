package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlbumPlaceRequest {
    @NotBlank(message = "名称不能为空")
    private String name;

    private String address = "";
    private String city = "";

    @NotNull(message = "纬度不能为空")
    private Double lat;

    @NotNull(message = "经度不能为空")
    private Double lng;

    private String note = "";
    private Integer sortOrder = 0;
    private String status = "DRAFT";
}
