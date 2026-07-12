package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.CommentRequest;
import com.harvey.digitalgarden.dto.CommentVO;
import com.harvey.digitalgarden.entity.Comment;
import com.harvey.digitalgarden.repository.CommentRepository;
import com.harvey.digitalgarden.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
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
        return commentRepository.findByPostIdAndStatusOrderByInDtmDesc(postId, "APPROVED")
                .stream().map(this::toVO).toList();
    }

    public List<CommentVO> listByStatus(String status) {
        return commentRepository.findByStatusOrderByInDtmDesc(status)
                .stream().map(this::toVO).toList();
    }

    public CommentVO approve(Long id) {
        Comment c = commentRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("评论不存在"));
        c.setStatus("APPROVED");
        return toVO(commentRepository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        Comment c = commentRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("评论不存在"));
        c.setIsDeleted(true);
        c.setUpdateDtm(Instant.now().getEpochSecond());
        commentRepository.save(c);
    }

    private CommentVO toVO(Comment c) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setPostId(c.getPostId());
        vo.setNickname(c.getNickname());
        vo.setContent(c.getContent());
        vo.setStatus(c.getStatus());
        vo.setInDtm(c.getInDtm());
        return vo;
    }
}
