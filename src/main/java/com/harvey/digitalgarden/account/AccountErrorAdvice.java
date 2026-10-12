package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Prevent JDBC/SMTP exception details and submitted credentials leaking to clients. */
@Order(0)
@RestControllerAdvice(assignableTypes = AccountController.class)
public class AccountErrorAdvice {
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.getCode())
        .header("Cache-Control", "no-store")
        .body(Result.error(e.getCode(), e.getMessage()));
  }

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<?> malformed() {
    return ResponseEntity.badRequest()
        .header("Cache-Control", "no-store")
        .body(Result.error(400, "请求内容无效，请检查后重试"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> unexpected() {
    return ResponseEntity.status(503)
        .header("Cache-Control", "no-store")
        .body(Result.error(503, "账号服务暂时不可用，请稍后重试"));
  }
}
