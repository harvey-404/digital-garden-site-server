package com.harvey.digitalgarden.ledger.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvey.digitalgarden.account.*;
import com.harvey.digitalgarden.common.*;
import com.harvey.digitalgarden.tracker.TrackerClientAddress;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class LedgerGuestFilter extends OncePerRequestFilter {
    private final LedgerGuestService guests;private final AccountPolicy policy;private final TrackerClientAddress addresses;private final ObjectMapper json;
    public LedgerGuestFilter(LedgerGuestService guests,AccountPolicy policy,TrackerClientAddress addresses,ObjectMapper json) {this.guests=guests;this.policy=policy;this.addresses=addresses;this.json=json;}
    public static boolean allowed(String method,String path) {
        return switch(method) {
            case "GET" -> path.equals("/api/ledger/chat/session") || path.equals("/api/ledger/chat/history") || path.equals("/api/ledger/categories") || path.matches("/api/ledger/chat/messages/by-request/[A-Za-z0-9_-]{1,64}") || path.matches("/api/ledger/chat/attachments/[0-9]+/content");
            case "POST" -> path.equals("/api/ledger/chat/messages") || path.equals("/api/ledger/chat/attachments") || path.matches("/api/ledger/chat/drafts/[0-9]+/cancel");
            case "PUT","DELETE" -> path.matches("/api/ledger/chat/drafts/[0-9]+/items/[0-9]+");
            default -> false;
        };
    }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse out,FilterChain chain)throws ServletException,IOException {
        String path=r.getRequestURI();
        if(!path.startsWith("/api/ledger/") || path.equals("/api/ledger/auth/invite") || SecurityContextHolder.getContext().getAuthentication()!=null) {chain.doFilter(r,out);return;}
        if(!allowed(r.getMethod(),path)) {
            if(LedgerGuestService.cookie(r)!=null && path.startsWith("/api/ledger/chat/")){error(out,403,"请登录或注册后确认记账，草稿已保留");return;}
            chain.doFilter(r,out);return;
        }
        if(path.equals("/api/ledger/chat/session") && LedgerGuestService.cookie(r)==null && ("cross-site".equalsIgnoreCase(r.getHeader("Sec-Fetch-Site")) || r.getHeader("Origin")!=null && !policy.allowedOrigin(r))) {error(out,403,"请求来源无效，请从本站页面重试");return;}
        if(AccountPolicy.unsafe(r) && !policy.allowedOrigin(r)){error(out,403,"请求来源无效，请从本站页面重试");return;}
        try {
            var identity=guests.resolve(LedgerGuestService.cookie(r),addresses.resolve(r),path.equals("/api/ledger/chat/session") && r.getMethod().equals("GET"));
            r.setAttribute(LedgerGuestService.ATTRIBUTE,true);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity.principal(),null,List.of(new SimpleGrantedAuthority("ROLE_LEDGER"))));
            out.setHeader("Cache-Control","no-store");if(identity.token()!=null)guests.setCookie(out,identity.token());
        } catch(BusinessException e){error(out,e.getCode(),e.getMessage());return;}
        catch(Exception e){error(out,503,"游客服务暂时不可用，请稍后重试");return;}
        chain.doFilter(r,out);
    }
    private void error(HttpServletResponse out,int code,String message)throws IOException {out.setStatus(code);out.setContentType("application/json;charset=UTF-8");out.setHeader("Cache-Control","no-store");json.writeValue(out.getWriter(),Result.error(code,message));}
}
