package com.elsworne.stubline.spike.keycloakoauth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Keycloak-issued token tests; run only with the docker Maven profile. */
@Tag("docker")
@EnabledIfSystemProperty(named = "run.docker", matches = "true")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class KeycloakDockerTest {
  @Container
  static KeycloakContainer keycloak =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.8.1")
          .withRealmImportFile("/stubline-realm.json");

  @Value("${local.server.port}")
  int port;

  private final HttpClient http = HttpClient.newHttpClient();

  @DynamicPropertySource
  static void issuer(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> keycloak.getIssuerUrl("stubline"));
  }

  @Test
  void validTokenAccepted() throws Exception {
    assertEquals(200, get("/service", serviceToken()));
  }

  @Test
  void serviceAccountCannotCallBuyer() throws Exception {
    assertEquals(403, get("/buyer", serviceToken()));
  }

  @Test
  void tokenWithoutApiAudienceRejected() throws Exception {
    assertEquals(401, get("/service", tokenFor("foreign-service", "foreign-secret")));
  }

  @Test
  void buyerLoginWithPkceCallsBuyer() throws Exception {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    String redirect = "http://127.0.0.1:8765/callback";
    String issuer = keycloak.getIssuerUrl("stubline");
    String authorization =
        issuer
            + "/protocol/openid-connect/auth?response_type=code&client_id=buyer-cli&redirect_uri="
            + encode(redirect)
            + "&scope=openid&code_challenge_method=S256&code_challenge="
            + challenge;
    var browser =
        HttpClient.newBuilder()
            .cookieHandler(new LoopbackCookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    var form =
        browser.send(
            HttpRequest.newBuilder(URI.create(authorization)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    var matcher =
        Pattern.compile(
                "<form[^>]*id=\\\"kc-form-login\\\"[^>]*action=\\\"([^\\\"]+)\\\"", Pattern.DOTALL)
            .matcher(form.body());
    org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), form.body());
    String action = matcher.group(1).replace("&amp;", "&");
    var reply =
        browser.send(
            HttpRequest.newBuilder(URI.create(action))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "username=alice&password=alice-pass&credentialId="))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    for (int i = 0; i < 8 && reply.statusCode() / 100 == 3; i++) {
      URI next = URI.create(reply.headers().firstValue("location").orElseThrow());
      if (next.toString().startsWith(redirect)) {
        String code =
            java.util.Arrays.stream(next.getRawQuery().split("&"))
                .filter(part -> part.startsWith("code="))
                .map(part -> part.substring(5))
                .findFirst()
                .orElseThrow();
        String body =
            "grant_type=authorization_code&client_id=buyer-cli&redirect_uri="
                + encode(redirect)
                + "&code="
                + encode(code)
                + "&code_verifier="
                + encode(verifier);
        var tokenReply =
            http.send(
                HttpRequest.newBuilder(URI.create(keycloak.getTokenEndpoint("stubline")))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, tokenReply.statusCode(), tokenReply.body());
        String accessToken =
            new ObjectMapper().readTree(tokenReply.body()).path("access_token").asText();
        assertEquals(
            200,
            get("/buyer", accessToken),
            "Decoded access token claims: " + decodedClaims(accessToken));
        return;
      }
      reply =
          browser.send(
              HttpRequest.newBuilder(next).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    org.junit.jupiter.api.Assertions.fail("Login did not reach callback: " + reply.body());
  }

  /** Java does not treat HTTP localhost as a secure cookie context; browsers do. */
  private static final class LoopbackCookieManager extends CookieManager {
    LoopbackCookieManager() {
      super(null, CookiePolicy.ACCEPT_ALL);
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> headers)
        throws IOException {
      if ("http".equals(uri.getScheme())
          && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))) {
        return super.get(
            URI.create("https://" + uri.getRawAuthority() + uri.getRawPath()), headers);
      }
      return super.get(uri, headers);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String decodedClaims(String token) {
    return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
  }

  @Test
  void missingRoleForbidden() throws Exception {
    assertEquals(403, get("/operator", serviceToken()));
  }

  @Test
  void foreignTokenUnauthorized() throws Exception {
    assertEquals(401, get("/buyer", "foreign.invalid.token"));
  }

  private int get(String path, String token) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private String serviceToken() throws Exception {
    return tokenFor("stubline-service", "dev-service-secret");
  }

  private String tokenFor(String client, String secret) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(keycloak.getTokenEndpoint("stubline")))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "grant_type=client_credentials&client_id="
                        + client
                        + "&client_secret="
                        + secret))
            .build();
    var reply = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, reply.statusCode());
    return new ObjectMapper().readTree(reply.body()).path("access_token").asText();
  }
}
