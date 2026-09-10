package com.elsworne.stubline.spike.keycloakoauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Verifies Keycloak realm-role mapping without a Docker daemon. */
final class SecurityConfigTest {
  @Test
  void operatorRoleMapsToSpringAuthority() {
    var jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of("sub", "bob", "realm_access", Map.of("roles", List.of("operator"))));
    var auth = new SecurityConfig().roles().convert(jwt);
    assertEquals("bob", auth.getName());
    assertTrue(
        auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_operator")));
  }

  @Test
  void missingAudienceDoesNotCrash() {
    var jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "RS256"),
            Map.of("sub", "bob"));
    assertTrue(SecurityConfig.audienceValidator().validate(jwt).hasErrors());
  }
}
