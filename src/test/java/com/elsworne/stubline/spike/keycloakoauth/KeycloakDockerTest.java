package com.elsworne.stubline.spike.keycloakoauth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    assertEquals(200, get("/buyer", serviceToken()));
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
    var request =
        HttpRequest.newBuilder(URI.create(keycloak.getTokenEndpoint("stubline")))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "grant_type=client_credentials&client_id=stubline-service&client_secret=dev-service-secret"))
            .build();
    var reply = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, reply.statusCode());
    return new ObjectMapper().readTree(reply.body()).path("access_token").asText();
  }
}
