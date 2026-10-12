package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.ZoneId;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class AccountSecurity {
  private static final SecureRandom RANDOM = new SecureRandom();

  private AccountSecurity() {}

  static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String code() {
    return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Digest unavailable", e);
    }
  }

  static String codeHash(String secret, String email, String purpose, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal((email + "\n" + purpose + "\n" + code).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Digest unavailable", e);
    }
  }

  static boolean equal(String a, String b) {
    return a != null
        && b != null
        && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  static String email(String raw) {
    String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    if (value.length() > 254
        || !value.matches(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+"))
      throw BusinessException.badRequest("请输入有效的邮箱地址");
    return value;
  }

  static String password(String value) {
    if (value == null || value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72)
      throw BusinessException.badRequest("密码至少8位，且不超过72字节");
    return value;
  }

  static String timezone(String value) {
    String zone = value == null || value.isBlank() ? "Asia/Shanghai" : value.strip();
    try {
      ZoneId.of(zone);
    } catch (Exception e) {
      throw BusinessException.badRequest("请选择有效时区");
    }
    if (zone.length() > 64) throw BusinessException.badRequest("时区过长");
    return zone;
  }

  static String displayName(String value, String email) {
    String name =
        value == null || value.isBlank() ? email.substring(0, email.indexOf('@')) : value.strip();
    if (name.length() > 64) throw BusinessException.badRequest("昵称最多64个字符");
    return name;
  }
}
