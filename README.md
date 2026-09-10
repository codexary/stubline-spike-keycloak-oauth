English | [简体中文](README.zh-CN.md)

The published Keycloak port is bound to loopback because the local stack uses disposable admin/admin credentials. Do not expose these credentials on a shared network.

# Keycloak OAuth2 spike

This independent spike evaluates replacing Stubline's development token issuer with Keycloak. It uses Spring Boot 4.1.2, Spring Security's OAuth2 resource server, and Keycloak 26.8.1. Its conclusion informs a later identity-provider decision; it does not change the product's current hand-written validator.

Run `./mvnw -q verify` without Docker. Run `docker compose up -d` to import `realm/stubline-realm.json`, then `./mvnw -q spring-boot:run` in another terminal. The realm has `buyer-cli` (public authorization-code client, required S256 PKCE, loopback redirect), `stubline-service` (confidential client credentials), and users `alice` and `bob`; `bob` has the `operator` realm role. Passwords and client secret are local demo values. Run:

```sh
curl -i http://localhost:8083/public
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.keycloakoauth.OAuthClient -Dexec.args=buyer
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.keycloakoauth.OAuthClient -Dexec.args=service
./mvnw -q -Pdocker verify
```

The buyer client prints an authorization URL; open it, sign in as `alice`/`alice-pass`, and let Keycloak return to the local callback. It checks `state`, exchanges the code with its PKCE verifier and calls `/buyer`, which returns 200 and the subject. The service client obtains a token with its secret and calls `/service` without a browser. Its token gets 403 at `/buyer`; only a buyer role can call that route. `/public` returns 200 without a token; `/operator` requires `ROLE_operator`. The Docker tests also drive the login form and PKCE exchange with JDK `HttpClient`, check service and buyer separation, and reject a token without the API audience. `SecurityConfigTest` checks role mapping without Docker.

Spring's `issuer-uri` discovers JWKS and checks `iss`, signature and time claims, replacing Stubline's manual JWKS fetch, RS256 signature and `exp`/`nbf` checks. `oauth2ResourceServer().jwt()` parses the bearer header and returns 401 on invalid tokens; `authorizeHttpRequests()` is the counterpart of Stubline's route checks and 403 decision. `roles()` maps Keycloak's `realm_access.roles` into authorities. The imported realm adds `stubline-api` to access-token audiences, and the decoder validates it alongside issuer, signature and time. A service scope authorizes `/service`; a buyer role authorizes `/buyer`.

Kerberos's KDC authentication server and ticket-granting server roughly correspond to an identity provider. A ticket-granting ticket resembles an SSO session or refresh token; a service ticket resembles an access token scoped to a service audience. The analogy stops at cryptography and possession: Kerberos uses symmetric service keys and authenticators to prove possession, whereas this OAuth2 flow gives the client a signed bearer token protected in transit by TLS. PKCE protects the authorization-code exchange; it does not turn the access token into a proof-of-possession token.

## License

This repository is licensed under the [MIT License](LICENSE).

## About this repository

This repository contains a focused Stubline experiment. The README documents its commands and observed results.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.
