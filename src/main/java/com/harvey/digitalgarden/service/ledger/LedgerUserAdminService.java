package com.harvey.digitalgarden.service.ledger;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ledger.CreateLedgerUserResponse;
import com.harvey.digitalgarden.dto.ledger.LedgerUserAdminVO;
import com.harvey.digitalgarden.entity.LedgerCategory;
import com.harvey.digitalgarden.entity.LedgerUser;
import com.harvey.digitalgarden.ledger.LedgerInviteUtil;
import com.harvey.digitalgarden.repository.LedgerCategoryRepository;
import com.harvey.digitalgarden.repository.LedgerUserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerUserAdminService {

    private static final String[] DEFAULT_CATEGORY_NAMES = {
        "餐饮美食",
        "住房居家",
        "交通出行",
        "购物消费",
        "休闲娱乐",
        "医疗健康",
        "人情交际",
        "其他"
    };

    private final LedgerUserRepository userRepository;
    private final LedgerCategoryRepository categoryRepository;

    public LedgerUserAdminService(
            LedgerUserRepository userRepository, LedgerCategoryRepository categoryRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public CreateLedgerUserResponse createUser() {
        String userSn = LedgerInviteUtil.allocateUserSn(userRepository);
        String inviteCode = LedgerInviteUtil.allocateInviteCode(userRepository);

        LedgerUser user = new LedgerUser();
        user.setUserSn(userSn);
        user.setInviteCode(inviteCode);
        user.setStatus("active");
        userRepository.save(user);

        seedDefaultCategories(user.getId());
        return new CreateLedgerUserResponse(userSn, inviteCode);
    }

    public List<LedgerUserAdminVO> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "inDtm")).stream()
                .map(this::toAdminVO)
                .toList();
    }

    @Transactional
    public void setStatus(Long id, String status) {
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw BusinessException.badRequest("status must be active or disabled");
        }
        LedgerUser user =
                userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("账本用户不存在"));
        user.setStatus(status);
        userRepository.save(user);
    }

    private void seedDefaultCategories(Long userId) {
        List<LedgerCategory> categories = new ArrayList<>(DEFAULT_CATEGORY_NAMES.length);
        for (int i = 0; i < DEFAULT_CATEGORY_NAMES.length; i++) {
            LedgerCategory category = new LedgerCategory();
            category.setUserId(userId);
            category.setName(DEFAULT_CATEGORY_NAMES[i]);
            category.setIcon("");
            category.setSortOrder(i);
            categories.add(category);
        }
        categoryRepository.saveAll(categories);
    }

    private LedgerUserAdminVO toAdminVO(LedgerUser user) {
        LedgerUserAdminVO vo = new LedgerUserAdminVO();
        vo.setId(user.getId());
        vo.setUserSn(user.getUserSn());
        vo.setInviteCode(user.getInviteCode());
        vo.setDisplayName(user.getDisplayName());
        vo.setDefaultBudgetAmount(user.getDefaultBudgetAmount());
        vo.setStatus(user.getStatus());
        vo.setInDtm(user.getInDtm());
        vo.setUpdateDtm(user.getUpdateDtm());
        return vo;
    }
}
