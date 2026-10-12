package com.harvey.digitalgarden.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final com.harvey.digitalgarden.account.AccountLinks accountLinks;

    public JwtAuthFilter(JwtUtil jwtUtil, com.harvey.digitalgarden.account.AccountLinks accountLinks) {
        this.jwtUtil = jwtUtil;
        this.accountLinks = accountLinks;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getAttribute(com.harvey.digitalgarden.account.AccountPolicy.ATTRIBUTE) != null
                || request.getRequestURI().startsWith("/api/account/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseClaims(token);
                String role = claims.get("role", String.class);
                UsernamePasswordAuthenticationToken auth;
                if ("TRACKER".equals(role)) {
                    if (accountLinks.isBound("tracker", claims.get("uid", Number.class).longValue()))
                        throw new IllegalArgumentException("Site login required");
                    auth = new UsernamePasswordAuthenticationToken(
                            new com.harvey.digitalgarden.tracker.TrackerPrincipal(claims.get("uid", Number.class).longValue(), claims.getSubject()),
                            null, List.of(new SimpleGrantedAuthority("ROLE_TRACKER")));
                } else if ("LEDGER".equals(role)) {
                    long uid = claims.get("uid", Number.class).longValue();
                    if (accountLinks.isBound("ledger", uid))
                        throw new IllegalArgumentException("Site login required");
                    auth = new UsernamePasswordAuthenticationToken(
                            new LedgerPrincipal(uid, claims.getSubject()),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_LEDGER")));
                } else if ("ADMIN".equals(role) || role == null) {
                    auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                } else {
                    auth = null;
                }
                if (auth != null) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
