package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.CommentRequest;
import com.harvey.digitalgarden.dto.CommentVO;
import com.harvey.digitalgarden.entity.Comment;
import com.harvey.digitalgarden.repository.CommentRepository;
import com.harvey.digitalgarden.repository.PostRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    public CommentVO submit(Long postId, CommentRequest req) {
        if (!postRepository.existsById(postId)) {
            throw BusinessException.notFound("文章不存在");
        }
        Comment c = new Comment();
        c.setPostId(postId);
        c.setNickname(req.getNickname());
        c.setContent(req.getContent());
        c.setStatus("PENDING");
        return toVO(commentRepository.save(c));
    }

    public List<CommentVO> listApproved(Long postId) {
        return commentRepository.findByPostIdAndStatusOrderByCreatedAtDesc(postId, "APPROVED")
                .stream().map(this::toVO).toList();
    }

    public List<CommentVO> listByStatus(String status) {
        return commentRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(this::toVO).toList();
    }

    public CommentVO approve(Long id) {
        Comment c = commentRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("评论不存在"));
        c.setStatus("APPROVED");
        return toVO(commentRepository.save(c));
    }

    public void delete(Long id) {
        if (!commentRepository.existsById(id)) {
            throw BusinessException.notFound("评论不存在");
        }
        commentRepository.deleteById(id);
    }

    private CommentVO toVO(Comment c) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setPostId(c.getPostId());
        vo.setNickname(c.getNickname());
        vo.setContent(c.getContent());
        vo.setStatus(c.getStatus());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }
}
