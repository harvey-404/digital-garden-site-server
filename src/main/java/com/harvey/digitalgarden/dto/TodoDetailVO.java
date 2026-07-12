package com.harvey.digitalgarden.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TodoDetailVO extends TodoVO {
    private String planMd;
    private Long updateDtm;
}
