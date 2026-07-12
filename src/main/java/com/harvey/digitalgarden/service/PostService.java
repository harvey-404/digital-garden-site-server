package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.dto.PostDetailVO;
import com.harvey.digitalgarden.dto.PostRequest;
import com.harvey.digitalgarden.dto.PostVO;
import com.harvey.digitalgarden.entity.Post;
import com.harvey.digitalgarden.entity.PostTag;
import com.harvey.digitalgarden.entity.Tag;
import com.harvey.digitalgarden.repository.CommentRepository;
import com.harvey.digitalgarden.repository.PostLikeRepository;
import com.harvey.digitalgarden.repository.PostRepository;
import com.harvey.digitalgarden.repository.PostTagRepository;
import com.harvey.digitalgarden.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;

    public PostService(PostRepository postRepository,
                       TagRepository tagRepository,
                       PostTagRepository postTagRepository,
                       CommentRepository commentRepository,
                       PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
        this.postTagRepository = postTagRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "inDtm"));
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
        applyRequestFields(post, req);
        post = postRepository.save(post);
        syncTags(post.getId(), req.getTags());
        return toDetailVO(post);
    }

    @Transactional
    public PostDetailVO update(Long id, PostRequest req) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("文章不存在"));
        if (!post.getSlug().equals(req.getSlug()) && postRepository.existsBySlug(req.getSlug())) {
            throw BusinessException.conflict("slug 已存在");
        }
        applyRequestFields(post, req);
        post = postRepository.save(post);
        syncTags(post.getId(), req.getTags());
        return toDetailVO(post);
    }

    @Transactional
    public void delete(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("文章不存在"));
        long now = Instant.now().getEpochSecond();
        post.setIsDeleted(true);
        post.setUpdateDtm(now);
        postRepository.save(post);
        postTagRepository.softDeleteByPostId(id, now);
        commentRepository.softDeleteByPostId(id, now);
        postLikeRepository.softDeleteByPostId(id, now);
    }

    private void applyRequestFields(Post post, PostRequest req) {
        post.setTitle(req.getTitle());
        post.setSlug(req.getSlug());
        post.setContentMd(req.getContentMd());
        post.setSummary(req.getSummary() == null ? "" : req.getSummary());
        post.setCoverImage(req.getCoverImage() == null ? "" : req.getCoverImage());
        post.setStatus(req.getStatus());
    }

    private void syncTags(Long postId, Set<String> names) {
        Set<String> desired = new HashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.isBlank()) {
                    desired.add(name.trim());
                }
            }
        }

        Set<Long> desiredTagIds = new HashSet<>();
        long now = Instant.now().getEpochSecond();

        for (String name : desired) {
            Tag tag = tagRepository.findByName(name).orElseGet(() -> {
                Tag t = new Tag();
                t.setName(name);
                return tagRepository.save(t);
            });
            desiredTagIds.add(tag.getId());

            if (postTagRepository.findByPostIdAndTagId(postId, tag.getId()).isPresent()) {
                continue;
            }
            if (postTagRepository.restoreByPostIdAndTagId(postId, tag.getId(), now) == 0) {
                PostTag link = new PostTag();
                link.setPostId(postId);
                link.setTagId(tag.getId());
                postTagRepository.save(link);
            }
        }

        for (PostTag link : postTagRepository.findByPostId(postId)) {
            if (!desiredTagIds.contains(link.getTagId())) {
                link.setIsDeleted(true);
                link.setUpdateDtm(now);
                postTagRepository.save(link);
            }
        }
    }

    private List<String> loadTagNames(Long postId) {
        return postTagRepository.findByPostId(postId).stream()
                .map(pt -> tagRepository.findById(pt.getTagId()).map(Tag::getName).orElse(""))
                .filter(name -> !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
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
        vo.setTags(loadTagNames(p.getId()));
        vo.setInDtm(p.getInDtm());
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
        vo.setTags(loadTagNames(p.getId()));
        vo.setInDtm(p.getInDtm());
        vo.setUpdateDtm(p.getUpdateDtm());
        return vo;
    }
}
