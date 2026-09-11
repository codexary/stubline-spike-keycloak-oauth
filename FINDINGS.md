English | [简体中文](FINDINGS.zh-CN.md)

# Findings

## What we observed

The realm uses authorization code with S256 PKCE for a buyer and client credentials for a service. The tests distinguish buyer and service roles and reject a token without the API audience. Spring discovers JWKS through `issuer-uri`, but audience validation and Keycloak role mapping require explicit configuration. PKCE protects the code exchange; the access token remains a bearer token.

## Backend decision

Keycloak is a possible replacement for the development issuer. The backend must still validate audience, map roles, and enforce buyer ownership by `sub`; this experiment does not replace the current validator. See [backend ADR 0013 — Protect transport and buyer identity](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0013-protect-transport-and-buyer-identity.md).
