package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.dto.PostDetailVO;
import com.harvey.digitalgarden.dto.PostRequest;
import com.harvey.digitalgarden.dto.PostVO;
import com.harvey.digitalgarden.entity.Post;
import com.harvey.digitalgarden.entity.Tag;
import com.harvey.digitalgarden.repository.PostRepository;
import com.harvey.digitalgarden.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;

    public PostService(PostRepository postRepository, TagRepository tagRepository) {
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public PageResult<PostVO> listPublished(int page, int size, String tag) {
        Page<Post> result = (tag == null || tag.isBlank())
                ? postRepository.findByStatus("PUBLISHED", pageable(page, size))
                : postRepository.findPublishedByTag(tag, pageable(page, size));
        List<PostVO> items = result.getContent().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(result, items);
    }

    public PageResult<PostVO> search(String q, int page, int size) {
        Page<Post> result = postRepository.searchPublished(q == null ? "" : q, pageable(page, size));
        List<PostVO> items = result.getContent().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(result, items);
    }

    public PageResult<PostVO> listAll(int page, int size) {
        Page<Post> result = postRepository.findAll(pageable(page, size));
        List<PostVO> items = result.getContent().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(result, items);
    }

    @Transactional
    public PostDetailVO getPublishedBySlug(String slug) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> BusinessException.notFound("文章不存在"));
        if (!"PUBLISHED".equals(post.getStatus())) {
            throw BusinessException.notFound("文章不存在");
        }
        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);
        return toDetailVO(post);
    }

    @Transactional
    public PostDetailVO create(PostRequest req) {
        if (postRepository.existsBySlug(req.getSlug())) {
            throw BusinessException.conflict("slug 已存在");
        }
        Post post = new Post();
        applyRequest(post, req);
        return toDetailVO(postRepository.save(post));
    }

    @Transactional
    public PostDetailVO update(Long id, PostRequest req) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("文章不存在"));
        if (!post.getSlug().equals(req.getSlug()) && postRepository.existsBySlug(req.getSlug())) {
            throw BusinessException.conflict("slug 已存在");
        }
        applyRequest(post, req);
        return toDetailVO(postRepository.save(post));
    }

    @Transactional
    public void delete(Long id) {
        if (!postRepository.existsById(id)) {
            throw BusinessException.notFound("文章不存在");
        }
        postRepository.deleteById(id);
    }

    private void applyRequest(Post post, PostRequest req) {
        post.setTitle(req.getTitle());
        post.setSlug(req.getSlug());
        post.setContentMd(req.getContentMd());
        post.setSummary(req.getSummary());
        post.setCoverImage(req.getCoverImage());
        post.setStatus(req.getStatus());
        post.setTags(resolveTags(req.getTags()));
    }

    private Set<Tag> resolveTags(Set<String> names) {
        Set<Tag> tags = new HashSet<>();
        if (names == null) return tags;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            Tag tag = tagRepository.findByName(name.trim()).orElseGet(() -> {
                Tag t = new Tag();
                t.setName(name.trim());
                return tagRepository.save(t);
            });
            tags.add(tag);
        }
        return tags;
    }

    public PostVO toVO(Post p) {
        PostVO vo = new PostVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setSlug(p.getSlug());
        vo.setSummary(p.getSummary());
        vo.setCoverImage(p.getCoverImage());
        vo.setStatus(p.getStatus());
        vo.setViewCount(p.getViewCount());
        vo.setLikeCount(p.getLikeCount());
        vo.setTags(p.getTags().stream().map(Tag::getName).collect(Collectors.toList()));
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }

    public PostDetailVO toDetailVO(Post p) {
        PostDetailVO vo = new PostDetailVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setSlug(p.getSlug());
        vo.setContentMd(p.getContentMd());
        vo.setSummary(p.getSummary());
        vo.setCoverImage(p.getCoverImage());
        vo.setStatus(p.getStatus());
        vo.setViewCount(p.getViewCount());
        vo.setLikeCount(p.getLikeCount());
        vo.setTags(p.getTags().stream().map(Tag::getName).collect(Collectors.toList()));
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
