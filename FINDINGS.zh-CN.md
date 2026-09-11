[English](FINDINGS.md) | 简体中文

# 实验结论

## 观察

realm 为买家使用带 S256 PKCE 的授权码流程，为服务使用 client credentials。测试区分买家与服务角色，并拒绝缺少 API audience 的 token。Spring 可通过 `issuer-uri` 发现 JWKS，但 audience 校验和 Keycloak 角色映射仍要明确配置。PKCE 保护授权码交换，access token 仍是 bearer token。

## 对后端的影响

Keycloak 可以接替开发用的 token issuer；后端仍须校验 audience、映射角色，并按 `sub` 限制买家数据访问。这个实验没有替换当前产品验证器。参见[后端 ADR 0013：Protect transport and buyer identity](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0013-protect-transport-and-buyer-identity.md)。
