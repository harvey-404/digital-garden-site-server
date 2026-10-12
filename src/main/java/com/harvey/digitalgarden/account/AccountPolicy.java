package com.harvey.digitalgarden.account;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccountPolicy {
  public static final String COOKIE = "dg_session";
  public static final String ATTRIBUTE = "site.account";
  private final Set<String> origins;
  private final boolean secure;
  private final String serviceKey;

  public AccountPolicy(
      @Value("${app.account.origin:https://www.harvey404.com}") String origins,
      @Value("${app.account.secure-cookie:true}") boolean secure,
      @Value("${app.account.service-key:}") String serviceKey) {
    this.origins =
        Set.copyOf(
            Arrays.stream(origins.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList());
    this.secure = secure;
    this.serviceKey = serviceKey;
  }

  public boolean allowedOrigin(HttpServletRequest r) {
    String origin = r.getHeader("Origin");
    return origin != null && origins.contains(origin);
  }

  public boolean trustedService(String key) {
    return serviceKey.length() >= 32 && AccountSecurity.equal(serviceKey, key);
  }

  public static boolean unsafe(HttpServletRequest r) {
    return !Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod());
  }

  public static String cookie(HttpServletRequest r) {
    if (r.getCookies() == null) return null;
    String result = null;
    for (Cookie c : r.getCookies())
      if (COOKIE.equals(c.getName())) {
        if (result != null) return "invalid-duplicate-cookie";
        result = c.getValue();
      }
    return result;
  }

  public void setCookie(HttpServletResponse r, String token, long maxAge) {
    var cookie =
        org.springframework.http.ResponseCookie.from(COOKIE, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build();
    r.addHeader("Set-Cookie", cookie.toString());
  }
}
