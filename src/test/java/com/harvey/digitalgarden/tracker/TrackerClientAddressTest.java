package com.harvey.digitalgarden.tracker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrackerClientAddressTest {
  @Test
  void trustsOnlyExplicitProxyCidrs() {
    var resolver = new TrackerClientAddress("127.0.0.1/32,::1/128,192.168.1.0/24");
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.9");
    request.addHeader("X-Real-IP", "198.51.100.2");
    assertEquals("203.0.113.9", resolver.resolve(request));
    request.setRemoteAddr("192.168.1.4");
    assertEquals("198.51.100.2", resolver.resolve(request));
    request.removeHeader("X-Real-IP");
    request.addHeader("X-Forwarded-For", "198.51.100.2,10.0.0.1");
    assertEquals("192.168.1.4", resolver.resolve(request));
  }

  @Test
  void boundedAuthThrottle() {
    var service = new TrackerAuthService(null, null, null);
    for (int i = 0; i < 30; i++) service.throttle("203.0.113.4");
    assertThrows(TrackerException.class, () -> service.throttle("203.0.113.4"));
    assertDoesNotThrow(() -> service.throttle("203.0.113.5"));
  }
}
