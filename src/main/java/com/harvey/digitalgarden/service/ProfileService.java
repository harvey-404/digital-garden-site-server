package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ProfileRequest;
import com.harvey.digitalgarden.dto.ProfileVO;
import com.harvey.digitalgarden.entity.SiteProfile;
import com.harvey.digitalgarden.repository.SiteProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {

    private final SiteProfileRepository repository;

    public ProfileService(SiteProfileRepository repository) {
        this.repository = repository;
    }

    public ProfileVO get() {
        SiteProfile p = repository.findById(1L)
                .orElseThrow(() -> BusinessException.notFound("站点信息未初始化"));
        return toVO(p);
    }

    public ProfileVO update(ProfileRequest req) {
        SiteProfile p = repository.findById(1L).orElseGet(() -> {
            SiteProfile np = new SiteProfile();
            np.setId(1L);
            return np;
        });
        p.setDisplayName(req.getDisplayName());
        p.setAvatarUrl(req.getAvatarUrl());
        p.setBio(req.getBio());
        p.setSocialLinks(req.getSocialLinks());
        return toVO(repository.save(p));
    }

    private ProfileVO toVO(SiteProfile p) {
        ProfileVO vo = new ProfileVO();
        vo.setDisplayName(p.getDisplayName());
        vo.setAvatarUrl(p.getAvatarUrl());
        vo.setBio(p.getBio());
        vo.setSocialLinks(p.getSocialLinks());
        return vo;
    }
}
