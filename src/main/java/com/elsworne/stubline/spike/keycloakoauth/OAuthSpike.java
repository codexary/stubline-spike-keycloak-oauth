package com.elsworne.stubline.spike.keycloakoauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the Keycloak-backed resource server. */
@SpringBootApplication
public class OAuthSpike {
  public static void main(String[] args) {
    SpringApplication.run(OAuthSpike.class, args);
  }
}
