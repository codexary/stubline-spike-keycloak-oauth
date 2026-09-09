package com.elsworne.stubline.spike.keycloakoauth;

import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
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
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(roles())))
        .build();
  }

  @Bean
  Converter<Jwt, AbstractAuthenticationToken> roles() {
    return jwt -> {
      var authorities = new ArrayList<GrantedAuthority>();
      var access = jwt.getClaimAsMap("realm_access");
      if (access != null && access.get("roles") instanceof java.util.List<?> roles)
        for (Object role : roles) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
      return new JwtAuthenticationToken(jwt, authorities);
    };
  }
}
