package com.harvey.digitalgarden.tracker;

import com.harvey.digitalgarden.common.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.harvey.digitalgarden.tracker")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrackerExceptionHandler {
  @ExceptionHandler(TrackerException.class)
  public Result<?> expected(TrackerException e) {
    return Result.error(e.code, e.getMessage());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public Result<?> malformed(Exception e) {
    return Result.error(400, "请求格式不正确，请检查输入");
  }

  @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
  public Result<?> duplicate(Exception e) {
    return Result.error(409, "名称或请求已存在，请刷新后重试");
  }

  @ExceptionHandler(Exception.class)
  public Result<?> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(TrackerExceptionHandler.class)
        .error("Tracker request failed", e);
    return Result.error(500, "服务暂时不可用，请稍后重试");
  }
}
