package com.harvey.digitalgarden.tracker;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class TrackerClientAddress {
  private final List<IpAddressMatcher> trusted;

  public TrackerClientAddress(
      @Value("${app.tracker.trusted-proxies:127.0.0.1/32,::1/128}") String proxies) {
    trusted =
        Arrays.stream(proxies.split(","))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .map(IpAddressMatcher::new)
            .toList();
  }

  public String resolve(HttpServletRequest request) {
    String peer = request.getRemoteAddr(), forwarded = request.getHeader("X-Real-IP");
    if (trusted.stream().anyMatch(m -> m.matches(peer))
        && forwarded != null
        && forwarded.length() <= 45
        && forwarded.matches("[0-9a-fA-F:.]+")) return forwarded;
    return peer;
  }
}
