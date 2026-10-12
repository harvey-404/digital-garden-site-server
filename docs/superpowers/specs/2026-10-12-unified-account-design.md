# 全站统一邮箱账号

用户已确认邮箱加密码、阿里云邮件推送，以及域名下所有用户工具共用账号并保留原数据。

## 用户体验

网站提供统一注册、登录、密码找回和账号中心。邮箱经验证码验证后才能注册；平时以邮箱和密码登录。记账、打卡、饮食陪伴及孙子军师共享登录状态。博客公开内容继续可浏览。管理后台保留独立管理员权限。旧用户在账号中心提供旧邀请码、旧用户名密码或原登录凭证完成绑定；不按同名或相同数字编号自动合并。

## 认证与接口

Spring Boot 网站服务保存 account、session、email challenge 和 module link。随机会话凭证仅放 host-only、HttpOnly、SameSite=Lax、Path=/ 的 dg_session Cookie；生产 Secure，默认30天。数据库只保存凭证摘要。密码重置撤销全部会话。所有 Cookie 写请求验证 Origin；邮件发送与登录按地址/IP限速。邮箱验证码只保存摘要，10分钟到期、最多5次尝试、用途隔离、一次性消费。

统一 API /api/account：GET me、GET status；POST code {email,purpose:register|reset}；POST register {email,password,code,displayName?,timezone?}；POST login {email,password}；POST reset-password {email,code,password}；POST logout；POST links/ledger {inviteCode? ,token?}；POST links/tracker {username?,password?,token?}。均采用现有 {code,message,data} 格式。me/register/login 的 data 为 {id,email,displayName,timezone,links:{ledger,tracker}}。code 返回 {retryAfterSeconds:60}。status 返回 {mailConfigured}。

跨后端 GET /api/account/internal/session：服务持有 X-Site-Service-Key 以及 X-Site-Session 原始凭证，返回同样的 data 账号结构；无效会话真实 HTTP 401，错误服务密钥403。业务服务不接受客户端提供的统一用户编号。浏览器只持有 Cookie，跨服务由后端验证。域名的 Cookie 自动传至 /diet-api/ 和 /sunzi/。服务调用须使用HTTPS或受控私网，超时与验证失败均拒绝访问。

## 原数据与业务身份

各工具原用户主键及所有业务表不改动。统一账号通过唯一 module link 绑定现有用户。第一次访问工具才分配新身份。旧身份已被绑定时禁止再次绑定。已创建但无业务记录的新身份允许替换为旧身份；已有数据则返回明确冲突，不静默丢弃。原凭证在迁移期可证明归属，绑定后不允许凭旧凭证绕过统一账号登录。饮食与军师在各自数据库保存唯一关联并验证会话。军师保留历史所有权和私有历史分区。注册不会赋予管理员权限。

## 邮件与部署

阿里云 DirectMail SMTP：默认杭州 smtpdm.aliyun.com:465，TLS证书校验、连接/读取超时。发信地址和SMTP密码仅环境变量，未配置时明确拒绝发送，不伪造发送成功。配置准备状态可查，禁止在生产展示验证码。用户尚未开通邮件推送，交付包括开通、DNS验证、SMTP密码、服务器环境变量说明；上线前先备份各数据库，迁移只增加表/列。回滚必须保留统一认证校验及新增数据，不允许恢复公开的邀请码登录绕过已绑定账号。

绑定和业务写入须共享身份锁。主站同步工具请求以 READ_COMMITTED 事务持有账号锁至业务提交，避免等待绑定后继续读取旧快照；登录和密码重置也显式使用 READ_COMMITTED 并锁定账号，确保重置撤销并发登录会话。饮食服务以 SQLite 写预留和退休空身份检查防止绑定期间的迟到写入，流式完成事件在持久化后发送。

## 验证

真实HTTP测试验证注册验证码用途、过期、限速、单次使用、密码重置、Cookie安全属性、CSRF、用户数据隔离、管理员隔离、旧数据绑定和绑定冲突。各独立服务测试会话校验失败、旧身份关联及原记录保留。前端构建及浏览器测试统一入口、跳回原工具、全站退出和账号中心。真实邮件须在用户完成阿里云配置后再验证。
