package com.harvey.digitalgarden.account;

/** Server-side delivery boundary; implementations must never log verification codes. */
public interface AccountMailSender {
  boolean configured();

  void sendCode(String email, String purpose, String code);
}
