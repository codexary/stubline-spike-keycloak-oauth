package com.elsworne.stubline.spike.keycloakoauth;

import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, buyer and operator endpoints for comparing resource-server rules. */
@RestController
public class ApiController {
  @GetMapping("/public")
  public Map<String, String> publicEndpoint() {
    return Map.of("status", "public");
  }

  @GetMapping("/buyer")
  public Map<String, String> buyer(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("sub", jwt.getSubject());
  }

  @GetMapping("/operator")
  public Map<String, String> operator(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("sub", jwt.getSubject());
  }
}
