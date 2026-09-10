package com.elsworne.stubline.spike.keycloakoauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Runs authorization-code PKCE and client-credentials flows against the imported realm. */
public final class OAuthClient {
  private static final String REALM =
      "http://localhost:8080/realms/stubline/protocol/openid-connect";
  private static final String REDIRECT = "http://127.0.0.1:8765/callback";
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper JSON = new ObjectMapper();

  private OAuthClient() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !(args[0].equals("buyer") || args[0].equals("service")))
      throw new IllegalArgumentException("OAuthClient buyer|service");
    String token = args[0].equals("buyer") ? buyerToken() : serviceToken();
    String path = args[0].equals("buyer") ? "/buyer" : "/service";
    var response =
        HTTP.send(
            HttpRequest.newBuilder(URI.create("http://localhost:8083" + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    System.out.println(path + " HTTP " + response.statusCode() + " " + response.body());
  }

  private static String buyerToken() throws Exception {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    String state = UUID.randomUUID().toString();
    var code = new CompletableFuture<String>();
    var callback = HttpServer.create(new InetSocketAddress("127.0.0.1", 8765), 0);
    callback.createContext(
        "/callback",
        exchange -> {
          var values =
              java.util.Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                  .map(part -> part.split("=", 2))
                  .collect(
                      java.util.stream.Collectors.toMap(p -> p[0], p -> p.length > 1 ? p[1] : ""));
          if (state.equals(values.get("state"))) code.complete(values.get("code"));
          else code.completeExceptionally(new SecurityException("OAuth state mismatch"));
          byte[] bytes = "You may close this tab.".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    callback.start();
    try {
      System.out.println(
          "Open: "
              + REALM
              + "/auth?response_type=code&client_id=buyer-cli&redirect_uri="
              + enc(REDIRECT)
              + "&scope=openid&state="
              + enc(state)
              + "&code_challenge_method=S256&code_challenge="
              + enc(challenge));
      return exchange(
          "grant_type=authorization_code&client_id=buyer-cli&code="
              + enc(code.get(120, TimeUnit.SECONDS))
              + "&redirect_uri="
              + enc(REDIRECT)
              + "&code_verifier="
              + enc(verifier));
    } finally {
      callback.stop(0);
    }
  }

  private static String serviceToken() throws Exception {
    return exchange(
        "grant_type=client_credentials&client_id=stubline-service&client_secret="
            + enc("dev-service-secret"));
  }

  private static String exchange(String form) throws Exception {
    var response =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(REALM + "/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) throw new IllegalStateException(response.body());
    return JSON.readTree(response.body()).path("access_token").asText();
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
