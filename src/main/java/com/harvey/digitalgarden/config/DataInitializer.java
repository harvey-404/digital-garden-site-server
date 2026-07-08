package com.harvey.digitalgarden.config;

import com.harvey.digitalgarden.entity.AdminUser;
import com.harvey.digitalgarden.entity.SiteProfile;
import com.harvey.digitalgarden.repository.AdminUserRepository;
import com.harvey.digitalgarden.repository.SiteProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AdminUserRepository adminRepo;
    private final SiteProfileRepository profileRepo;
    private final PasswordEncoder encoder;
    private final String defaultUsername;
    private final String defaultPassword;

    public DataInitializer(AdminUserRepository adminRepo,
                           SiteProfileRepository profileRepo,
                           PasswordEncoder encoder,
                           @Value("${app.admin.default-username}") String defaultUsername,
                           @Value("${app.admin.default-password}") String defaultPassword) {
        this.adminRepo = adminRepo;
        this.profileRepo = profileRepo;
        this.encoder = encoder;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        if (!adminRepo.existsByUsername(defaultUsername)) {
            AdminUser admin = new AdminUser();
            admin.setUsername(defaultUsername);
            admin.setPasswordHash(encoder.encode(defaultPassword));
            adminRepo.save(admin);
        }
        if (profileRepo.findById(1L).isEmpty()) {
            SiteProfile profile = new SiteProfile();
            profile.setId(1L);
            profile.setDisplayName("Harvey");
            profile.setBio("欢迎来到我的数字花园。");
            profile.setSocialLinks("[]");
            profileRepo.save(profile);
        }
    }
}
