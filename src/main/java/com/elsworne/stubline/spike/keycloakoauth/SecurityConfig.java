package com.elsworne.stubline.spike.keycloakoauth;

import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/** Maps Keycloak realm roles to Spring Security authorities. */
@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            rules ->
                rules
                    .requestMatchers("/public")
                    .permitAll()
                    .requestMatchers("/operator")
                    .hasRole("operator")
                    .requestMatchers("/buyer")
                    .hasRole("buyer")
                    .requestMatchers("/service")
                    .hasAuthority("SCOPE_service")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(roles())))
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
    var decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
    OAuth2TokenValidator<Jwt> audience = audienceValidator();
    decoder.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audience));
    return decoder;
  }

  static OAuth2TokenValidator<Jwt> audienceValidator() {
    return jwt ->
        jwt.getAudience() != null && jwt.getAudience().contains("stubline-api")
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Missing stubline-api audience", null));
  }

  @Bean
  Converter<Jwt, AbstractAuthenticationToken> roles() {
    return jwt -> {
      var authorities = new ArrayList<GrantedAuthority>();
      var access = jwt.getClaimAsMap("realm_access");
      for (String scope :
          jwt.getClaimAsString("scope") == null
              ? new String[0]
              : jwt.getClaimAsString("scope").split(" "))
        authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
      if (access != null && access.get("roles") instanceof java.util.List<?> roles)
        for (Object role : roles) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
      return new JwtAuthenticationToken(jwt, authorities);
    };
  }
}
