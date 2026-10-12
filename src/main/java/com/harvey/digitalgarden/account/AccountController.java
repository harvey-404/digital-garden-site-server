package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.*;
import com.harvey.digitalgarden.tracker.TrackerClientAddress;
import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
  private final AccountService accounts;
  private final AccountChallengeService codes;
  private final AccountMailSender mail;
  private final AccountLinks links;
  private final AccountPolicy policy;
  private final TrackerClientAddress addresses;
  private final AccountRateLimit rate;

  public AccountController(
      AccountService accounts,
      AccountChallengeService codes,
      AccountMailSender mail,
      AccountLinks links,
      AccountPolicy policy,
      TrackerClientAddress addresses,
      AccountRateLimit rate) {
    this.accounts = accounts;
    this.codes = codes;
    this.mail = mail;
    this.links = links;
    this.policy = policy;
    this.addresses = addresses;
    this.rate = rate;
  }

  public record CodeInput(String email, String purpose) {}

  public record RegisterInput(
      String email, String password, String code, String displayName, String timezone) {}

  public record LoginInput(String email, String password) {}

  public record ResetInput(String email, String code, String password) {}

  public record LinkInput(String token, String inviteCode, String username, String password) {}

  @GetMapping("/status")
  public Result<?> status(HttpServletResponse r) {
    noCache(r);
    return Result.success(Map.of("mailConfigured", mail.configured()));
  }

  @GetMapping("/me")
  public Result<?> me(HttpServletRequest r, HttpServletResponse out) {
    noCache(out);
    return Result.success(accounts.view(current(r)));
  }

  @PostMapping("/code")
  public Result<?> code(@RequestBody CodeInput in, HttpServletRequest r, HttpServletResponse out) {
    noCache(out);
    codes.send(in.email(), in.purpose(), addresses.resolve(r));
    return Result.success(Map.of("retryAfterSeconds", 60));
  }

  @PostMapping("/register")
  public Result<?> register(
      @RequestBody RegisterInput in, HttpServletRequest r, HttpServletResponse out) {
    noCache(out);
    var login =
        accounts.register(
            in.email(),
            in.password(),
            in.code(),
            in.displayName(),
            in.timezone(),
            addresses.resolve(r));
    policy.setCookie(out, login.token(), accounts.ttlSeconds());
    return Result.success(login.user());
  }

  @PostMapping("/login")
  public Result<?> login(
      @RequestBody LoginInput in, HttpServletRequest r, HttpServletResponse out) {
    noCache(out);
    var login = accounts.login(in.email(), in.password(), addresses.resolve(r));
    policy.setCookie(out, login.token(), accounts.ttlSeconds());
    return Result.success(login.user());
  }

  @PostMapping("/reset-password")
  public Result<?> reset(
      @RequestBody ResetInput in, HttpServletRequest r, HttpServletResponse out) {
    noCache(out);
    accounts.reset(in.email(), in.code(), in.password(), addresses.resolve(r));
    policy.setCookie(out, "", 0);
    return Result.success();
  }

  @PostMapping("/logout")
  public Result<?> logout(HttpServletRequest r, HttpServletResponse out) {
    noCache(out);
    accounts.logout(AccountPolicy.cookie(r));
    policy.setCookie(out, "", 0);
    return Result.success();
  }

  @PostMapping("/links/{module}")
  public Result<?> link(
      @PathVariable String module,
      @RequestBody LinkInput in,
      HttpServletRequest r,
      HttpServletResponse out) {
    noCache(out);
    var a = current(r);
    rate.consume("link:" + a.id() + ":" + addresses.resolve(r), 30, 3600000);
    links.bind(a, module, in.token(), in.inviteCode(), in.username(), in.password());
    return Result.success(accounts.view(a));
  }

  @GetMapping("/internal/session")
  public ResponseEntity<?> introspect(
      @RequestHeader(value = "X-Site-Service-Key", required = false) String key,
      @RequestHeader(value = "X-Site-Session", required = false) String session,
      HttpServletResponse out) {
    noCache(out);
    if (!policy.trustedService(key))
      return ResponseEntity.status(403).body(Result.error(403, "Forbidden"));
    try {
      return ResponseEntity.ok(Result.success(accounts.view(accounts.authenticate(session))));
    } catch (BusinessException e) {
      return ResponseEntity.status(401).body(Result.error(401, "Invalid session"));
    }
  }

  private AccountService.Account current(HttpServletRequest r) {
    if (r.getAttribute(AccountPolicy.ATTRIBUTE) instanceof AccountService.Account a) return a;
    throw AccountService.unauthorized();
  }

  private void noCache(HttpServletResponse r) {
    r.setHeader("Cache-Control", "no-store");
    r.setHeader("Pragma", "no-cache");
  }
}
