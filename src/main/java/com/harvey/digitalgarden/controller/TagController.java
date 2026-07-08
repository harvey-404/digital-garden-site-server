package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.entity.Tag;
import com.harvey.digitalgarden.repository.TagRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @GetMapping
    public Result<List<String>> list() {
        return Result.success(tagRepository.findAll().stream().map(Tag::getName).toList());
    }
}
