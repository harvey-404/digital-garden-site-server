package com.harvey.digitalgarden.dto.game;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class SemanticWordBatchRequest {
    @NotEmpty(message = "词列表不能为空")
    private List<String> words;
}
