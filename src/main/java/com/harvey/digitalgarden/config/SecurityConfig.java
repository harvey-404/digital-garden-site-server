package com.harvey.digitalgarden.config;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.common.ResultCode;
import com.harvey.digitalgarden.security.JwtAuthFilter;
import com.harvey.digitalgarden.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/tracker/auth/register", "/api/tracker/auth/login").permitAll()
                .requestMatchers("/api/tracker/**").hasRole("TRACKER")
                .requestMatchers("/api/ledger/auth/invite").permitAll()
                .requestMatchers("/api/ledger/**").hasRole("LEDGER")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(200);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(new ObjectMapper().writeValueAsString(
                        Result.error(ResultCode.UNAUTHORIZED, "未登录或登录已过期")));
            }).accessDeniedHandler((req, res, ex) -> {
                if (req.getRequestURI().startsWith("/api/tracker/")) {
                    res.setStatus(200);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(new ObjectMapper().writeValueAsString(
                            Result.error(403, "此账号无权访问行为记录")));
                } else {
                    new org.springframework.security.web.access.AccessDeniedHandlerImpl()
                            .handle(req, res, ex);
                }
            }))
            .addFilterBefore(new JwtAuthFilter(jwtUtil),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
