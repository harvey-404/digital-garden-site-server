package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class AliyunAccountMailSender implements AccountMailSender {
  private final JavaMailSenderImpl smtp;
  private final String from;
  private final boolean configured;

  public AliyunAccountMailSender(
      @Value("${app.account.mail.host:smtpdm.aliyun.com}") String host,
      @Value("${app.account.mail.port:465}") int port,
      @Value("${app.account.mail.username:}") String username,
      @Value("${app.account.mail.password:}") String password,
      @Value("${app.account.mail.from:}") String from,
      @Value("${app.account.mail.ssl:true}") boolean ssl) {
    this.from = from;
    configured = !host.isBlank() && !username.isBlank() && !password.isBlank() && !from.isBlank();
    smtp = new JavaMailSenderImpl();
    smtp.setHost(host);
    smtp.setPort(port);
    smtp.setUsername(username);
    smtp.setPassword(password);
    smtp.setDefaultEncoding("UTF-8");
    var p = smtp.getJavaMailProperties();
    p.setProperty("mail.smtp.auth", "true");
    p.setProperty("mail.smtp.ssl.enable", Boolean.toString(ssl));
    p.setProperty("mail.smtp.ssl.checkserveridentity", "true");
    p.setProperty("mail.smtp.connectiontimeout", "5000");
    p.setProperty("mail.smtp.timeout", "10000");
    p.setProperty("mail.smtp.writetimeout", "10000");
  }

  public boolean configured() {
    return configured;
  }

  public void sendCode(String email, String purpose, String code) {
    if (!configured) throw new BusinessException(503, "邮箱服务尚未配置，请稍后再试");
    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(from);
    msg.setTo(email);
    String action = "reset".equals(purpose) ? "重置密码" : "注册账号";
    msg.setSubject("Digital Garden · " + action + "验证码");
    msg.setText(
        "你正在"
            + action
            + "。\n\n验证码："
            + code
            + "\n\n10分钟内有效，请勿向他人提供。若非本人操作，请忽略此邮件。\n\nDigital Garden");
    try {
      smtp.send(msg);
    } catch (Exception e) {
      throw new BusinessException(503, "邮件暂时发送失败，请稍后重试或联系站长");
    }
  }
}
