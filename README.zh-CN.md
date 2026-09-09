[English](README.md) | 简体中文

Keycloak 的发布端口只绑定本机回环地址；本地栈使用一次性的 admin/admin 凭据，请勿在共享网络暴露。

# Keycloak OAuth2 小项目

这个独立小项目评估用 Keycloak 替换 Stubline 的开发 token issuer。版本固定为 Spring Boot 4.1.2、Spring Security OAuth2 resource server 和 Keycloak 26.8.1。结论供后续 identity provider 决策使用，不改变产品当前的手写验证器。

无需 Docker 即可运行 `./mvnw -q verify`。运行 `docker compose up -d` 导入 `realm/stubline-realm.json`，在另一终端运行 `./mvnw -q spring-boot:run`。realm 含 `buyer-cli`（公开 authorization code client、强制 S256 PKCE、本地 redirect）、`stubline-service`（机密 client credentials）以及 `alice`、`bob` 两位用户；`bob` 有 `operator` realm role。密码及 client secret 仅用于本地演示。

```sh
curl -i http://localhost:8083/public
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.keycloakoauth.OAuthClient -Dexec.args=buyer
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.keycloakoauth.OAuthClient -Dexec.args=service
./mvnw -q -Pdocker verify
```

buyer client 打印授权 URL；打开它，以 `alice`/`alice-pass` 登录，让 Keycloak 回跳本地 callback。client 检查 `state`，用 PKCE verifier 换取 token，调用 `/buyer` 返回 200 和 subject。service client 用 secret 换取 token，无需浏览器即可调用 `/buyer`。`/public` 无 token 返回 200；`/operator` 要求 `ROLE_operator`。Docker 测试检查有效 token、缺少 role 的 403 和外部 token 的 401；`SecurityConfigTest` 无 Docker 检查 role 映射。

Spring 的 `issuer-uri` 发现 JWKS，检查 `iss`、签名和时间 claims，对应 Stubline 手写的 JWKS 获取、RS256 签名、`exp`/`nbf` 检查。`oauth2ResourceServer().jwt()` 解析 bearer header，并在无效时返回 401；`authorizeHttpRequests()` 对应路由检查和 403 决策。`roles()` 将 Keycloak 的 `realm_access.roles` 映射到 authority。生产配置还应像 Stubline 一样固定算法、检查目标 API audience；本项目的导入 realm 未配置该 audience。

Kerberos 的 KDC authentication server 与 ticket-granting server 大致对应 identity provider。ticket-granting ticket 类似 SSO session 或 refresh token；service ticket 类似指定服务 audience 的 access token。类比在密码学和持有方式上失效：Kerberos 用对称 service key 与 authenticator 证明持有；这里客户端拿到受 TLS 传输保护的已签名 bearer token。PKCE 保护 authorization code 交换，不会把 access token 变成持有证明 token。

## 许可证

本仓库采用 [MIT License](LICENSE)。

## 关于本仓库

本仓库包含一项针对 Stubline 的独立实验。README 说明运行命令与观察结果。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。
