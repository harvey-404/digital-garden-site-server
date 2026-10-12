package com.harvey.digitalgarden.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvey.digitalgarden.common.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountSessionFilter extends OncePerRequestFilter {
  private final AccountService accounts;
  private final AccountLinks links;
  private final AccountPolicy policy;
  private final ObjectMapper json;
  private final org.springframework.transaction.support.TransactionTemplate moduleTransaction;

  public AccountSessionFilter(
      AccountService a,
      AccountLinks l,
      AccountPolicy p,
      ObjectMapper j,
      org.springframework.transaction.PlatformTransactionManager manager) {
    accounts = a;
    links = l;
    policy = p;
    json = j;
    moduleTransaction = new org.springframework.transaction.support.TransactionTemplate(manager);
    // Authentication reads precede the mapping lock. A fresh snapshot after
    // waiting is required to see a just-committed binding and business records.
    moduleTransaction.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse out, FilterChain chain)
      throws ServletException, IOException {
    String path = r.getRequestURI();
    String cookie = AccountPolicy.cookie(r);
    boolean account = path.startsWith("/api/account/");
    boolean ledger = path.startsWith("/api/ledger/") && !path.equals("/api/ledger/auth/invite");
    boolean tracker =
        path.startsWith("/api/tracker/")
            && !Set.of("/api/tracker/auth/login", "/api/tracker/auth/register").contains(path);
    if (AccountPolicy.unsafe(r)
        && (account || cookie != null && (ledger || tracker))
        && !policy.allowedOrigin(r)) {
      error(out, 403, "请求来源无效，请从本站页面重试");
      return;
    }
    boolean requiredAccount =
        path.equals("/api/account/me") || path.startsWith("/api/account/links/");
    if (cookie != null && (ledger || tracker)) {
      // All module handlers are synchronous. Retain the account/mapping lock through
      // business writes, so proof-based binding cannot orphan an in-flight record.
      var buffered = new org.springframework.web.util.ContentCachingResponseWrapper(out);
      try {
        moduleTransaction.executeWithoutResult(
            status -> {
              var a = accounts.authenticate(cookie);
              r.setAttribute(AccountPolicy.ATTRIBUTE, a);
              Object principal = links.resolve(a, ledger ? "ledger" : "tracker");
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      new UsernamePasswordAuthenticationToken(
                          principal,
                          null,
                          List.of(
                              new SimpleGrantedAuthority(
                                  ledger ? "ROLE_LEDGER" : "ROLE_TRACKER"))));
              try {
                chain.doFilter(r, buffered);
              } catch (IOException | ServletException e) {
                throw new IllegalStateException("Module request failed", e);
              }
            });
      } catch (org.springframework.transaction.UnexpectedRollbackException e) {
        // A controller advice may already have rendered a validation error while
        // its inner transaction marked the surrounding transaction rollback-only.
        boolean renderedError = false;
        try {
          renderedError =
              json.readTree(buffered.getContentAsByteArray()).path("code").asInt(0) != 0;
        } catch (Exception ignored) {
        }
        if (!renderedError) {
          buffered.resetBuffer();
          error(buffered, 503, "记录未保存，请稍后重试");
        }
      } catch (BusinessException e) {
        buffered.resetBuffer();
        error(buffered, e.getCode(), e.getMessage());
      } catch (Exception e) {
        buffered.resetBuffer();
        error(buffered, 503, "账号服务暂时不可用，请稍后重试");
      } finally {
        buffered.copyBodyToResponse();
      }
      return;
    }
    if (requiredAccount) {
      try {
        var a = accounts.authenticate(cookie);
        r.setAttribute(AccountPolicy.ATTRIBUTE, a);
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    a, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
      } catch (BusinessException e) {
        SecurityContextHolder.clearContext();
        error(out, e.getCode(), e.getMessage());
        return;
      } catch (Exception e) {
        SecurityContextHolder.clearContext();
        error(out, 503, "账号服务暂时不可用，请稍后重试");
        return;
      }
    }
    chain.doFilter(r, out);
  }

  private void error(HttpServletResponse out, int code, String message) throws IOException {
    out.setStatus(code >= 400 && code <= 599 ? code : 503);
    out.setContentType("application/json;charset=UTF-8");
    out.setHeader("Cache-Control", "no-store");
    json.writeValue(out.getWriter(), Result.error(code, message));
  }
}
