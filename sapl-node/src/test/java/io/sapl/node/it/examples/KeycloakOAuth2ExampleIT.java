/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.node.it.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.node.it.BaseIntegrationTest;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import reactor.test.StepVerifier;

/**
 * Smoke tests for the Docker Keycloak OAuth2 example
 * ({@code examples/docker/with-keycloak/}).
 * <p>
 * Starts a real Keycloak container with the example realm-export.json, then a
 * sapl-node container on the same Docker network. Acquires tokens via Resource
 * Owner Password grant and validates authorization decisions. Policy:
 * oauth2-permit-read (action==read AND resource==document). Algorithm:
 * PRIORITY_PERMIT, default DENY.
 * <p>
 * The Scalar tests replay the browser login the Scalar Authorize dialog runs
 * (authorization code + PKCE with the example's public client) without a
 * browser, and check that the resulting token is accepted by the node.
 */
@Testcontainers
@DisplayName("Keycloak OAuth2 Example Smoke Tests")
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class KeycloakOAuth2ExampleIT extends BaseIntegrationTest {

    private static final Path   POLICIES_DIR   = Path.of("examples/docker/with-keycloak/policies").toAbsolutePath();
    private static final Path   REALM_EXPORT   = Path.of("examples/docker/with-keycloak/keycloak/realm-export.json")
            .toAbsolutePath();
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:25.0";

    private static final String KEYCLOAK_INTERNAL_URL = "http://keycloak:8080";
    private static final String REALM_PATH            = "/realms/sapl-demo/protocol/openid-connect";
    private static final String SCALAR_CLIENT_ID      = "sapl-node-scalar";
    // Registered redirect URI of the example client. It is only compared by
    // Keycloak and never contacted, so no host port is bound to it.
    private static final String SCALAR_REDIRECT_URI = "http://localhost:8443/scalar";

    private static final Pattern      LOGIN_FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");
    private static final ObjectMapper JSON              = new ObjectMapper();

    @Test
    @DisplayName("default-user reading document is permitted via oauth2-permit-read policy")
    void whenDefaultUserReadsDocumentThenPermit() {
        try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
            keycloak.start();

            try (val saplNode = createSaplNodeOnNetwork(network)) {
                saplNode.start();

                val token        = acquireToken(keycloak, "default-user", "default123");
                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode))
                        .apiKey(token).allowInsecureTransport().build();
                val subscription = AuthorizationSubscription.of("user", "read", "document");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }
    }

    @Test
    @DisplayName("default-user deleting secret is denied (no matching policy, default DENY)")
    void whenDefaultUserDeletesSecretThenDeny() {
        try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
            keycloak.start();

            try (val saplNode = createSaplNodeOnNetwork(network)) {
                saplNode.start();

                val token        = acquireToken(keycloak, "default-user", "default123");
                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode))
                        .apiKey(token).allowInsecureTransport().build();
                val subscription = AuthorizationSubscription.of("user", "delete", "secret");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.DENY).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }
    }

    @Test
    @DisplayName("Scalar Authorize dialog is prefilled with the example's public client and Keycloak endpoints")
    void whenOauthEnabledThenOpenApiDocumentPrefillsScalarAuthorizeDialog() {
        try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
            keycloak.start();

            try (val saplNode = createSaplNodeOnNetwork(network)) {
                saplNode.start();

                val flow = WebClient.create(getHttpBaseUrl(saplNode)).get().uri("/v3/api-docs").retrieve()
                        .bodyToMono(JsonNode.class).block(Duration.ofSeconds(30))
                        .at("/components/securitySchemes/oauth2/flows/authorizationCode");

                assertThat(Map.of("authorizationUrl", flow.path("authorizationUrl").asString(), "tokenUrl",
                        flow.path("tokenUrl").asString(), "clientId", flow.path("x-scalar-client-id").asString(),
                        "redirectUri", flow.path("x-scalar-redirect-uri").asString()))
                        .containsExactlyInAnyOrderEntriesOf(
                                Map.of("authorizationUrl", KEYCLOAK_INTERNAL_URL + REALM_PATH + "/auth", "tokenUrl",
                                        KEYCLOAK_INTERNAL_URL + REALM_PATH + "/token", "clientId", SCALAR_CLIENT_ID,
                                        "redirectUri", "/scalar"));
            }
        }
    }

    @Test
    @DisplayName("Scalar login with the prefilled client yields a token the node accepts")
    void whenScalarClientCompletesPkceLoginThenTokenIsAcceptedByNode() throws Exception {
        try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
            keycloak.start();

            try (val saplNode = createSaplNodeOnNetwork(network)) {
                saplNode.start();

                val token        = scalarLogin(keycloak, SCALAR_REDIRECT_URI, "default-user", "default123");
                val pdp          = RemotePolicyDecisionPoint.builder().http().baseUrl(getHttpBaseUrl(saplNode))
                        .apiKey(token).allowInsecureTransport().build();
                val subscription = AuthorizationSubscription.of("user", "read", "document");

                StepVerifier.create(pdp.decide(subscription)).expectNext(AuthorizationDecision.PERMIT).thenCancel()
                        .verify(Duration.ofSeconds(30));
            }
        }
    }

    @Test
    @DisplayName("the public Scalar client refuses redirect URIs other than the registered Scalar page")
    void whenScalarClientUsesForeignRedirectUriThenKeycloakRefusesLogin() throws Exception {
        try (val network = Network.newNetwork(); val keycloak = createKeycloakContainer(network)) {
            keycloak.start();

            val pkce     = Pkce.create();
            val response = browserGet(newBrowser(), keycloak,
                    authorizeUrl(keycloak, "https://attacker.example.com/scalar", pkce, "state-foreign"));

            assertThat(response.statusCode()).isEqualTo(400);
        }
    }

    private KeycloakContainer createKeycloakContainer(Network network) {
        return new KeycloakContainer(KEYCLOAK_IMAGE).withNetwork(network).withNetworkAliases("keycloak")
                .withCopyFileToContainer(MountableFile.forHostPath(REALM_EXPORT),
                        "/opt/keycloak/data/import/realm-export.json")
                .withEnv("KC_HOSTNAME", "http://keycloak:8080").withEnv("KC_HOSTNAME_STRICT_BACKCHANNEL", "false")
                .withStartupTimeout(Duration.ofMinutes(5));
    }

    private GenericContainer<?> createSaplNodeOnNetwork(Network network) {
        return createSaplNodeContainer().withNetwork(network)
                .withFileSystemBind(POLICIES_DIR.toString(), "/pdp/data/", BindMode.READ_ONLY)
                .withEnv("IO_SAPL_PDP_EMBEDDED_POLICIESPATH", "/pdp/data").withEnv("IO_SAPL_NODE_ALLOWNOAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWBASICAUTH", "false").withEnv("IO_SAPL_NODE_ALLOWAPIKEYAUTH", "false")
                .withEnv("IO_SAPL_NODE_ALLOWOAUTH2AUTH", "true").withEnv("IO_SAPL_NODE_OAUTH_PDPIDCLAIM", "sapl_pdp_id")
                .withEnv("IO_SAPL_NODE_SCALAR_OAUTHCLIENTID", SCALAR_CLIENT_ID)
                .withEnv("IO_SAPL_NODE_REJECTONMISSINGPDPID", "false").withEnv("IO_SAPL_NODE_DEFAULTPDPID", "default")
                .withEnv("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI", "http://keycloak:8080/realms/sapl-demo")
                .withEnv("SERVER_SSL_ENABLED", "false");
    }

    private String acquireToken(KeycloakContainer keycloak, String username, String password) {
        val tokenUrl = keycloak.getAuthServerUrl() + "/realms/sapl-demo/protocol/openid-connect/token";

        val response = WebClient.create().post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password").with("client_id", "sapl-client")
                        .with("client_secret", "sapl-client-secret").with("username", username)
                        .with("password", password))
                .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(30));

        return response.get("access_token").asString();
    }

    /**
     * Replays the Scalar Authorize dialog: authorization request with PKCE,
     * Keycloak login form, authorization code from the redirect, code exchange.
     */
    private static String scalarLogin(KeycloakContainer keycloak, String redirectUri, String username, String password)
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        val browser   = newBrowser();
        val pkce      = Pkce.create();
        val loginPage = browserGet(browser, keycloak, authorizeUrl(keycloak, redirectUri, pkce, "state-scalar"));
        assertThat(loginPage.statusCode()).isEqualTo(200);

        val action = LOGIN_FORM_ACTION.matcher(loginPage.body());
        assertThat(action.find()).as("Keycloak login form present").isTrue();
        val loginUrl = toHostUrl(keycloak, action.group(1).replace("&amp;", "&"));

        val afterLogin = browser.send(
                HttpRequest.newBuilder(URI.create(loginUrl)).header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(form(Map.of("username", username, "password", password))).build(),
                HttpResponse.BodyHandlers.ofString());
        val callback   = afterLogin.headers().firstValue("Location").orElse("");
        assertThat(callback).startsWith(redirectUri + "?").contains("state=state-scalar").contains("code=");

        val code     = queryParameter(callback, "code");
        val tokenUrl = keycloak.getAuthServerUrl() + REALM_PATH + "/token";
        val tokens   = browser.send(
                HttpRequest.newBuilder(URI.create(tokenUrl)).header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(form(Map.of("grant_type", "authorization_code", "client_id", SCALAR_CLIENT_ID, "code",
                                code, "redirect_uri", redirectUri, "code_verifier", pkce.verifier())))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tokens.statusCode()).isEqualTo(200);
        return JSON.readTree(tokens.body()).path("access_token").asString();
    }

    private static String authorizeUrl(KeycloakContainer keycloak, String redirectUri, Pkce pkce, String state) {
        return keycloak.getAuthServerUrl() + REALM_PATH + "/auth?"
                + formString(Map.of("client_id", SCALAR_CLIENT_ID, "redirect_uri", redirectUri, "response_type", "code",
                        "scope", "openid", "state", state, "code_challenge", pkce.challenge(), "code_challenge_method",
                        "S256"));
    }

    private static HttpClient newBrowser() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * GET that follows Keycloak's redirects to its internal hostname by mapping
     * them back to the container's host-mapped address.
     */
    private static HttpResponse<String> browserGet(HttpClient browser, KeycloakContainer keycloak, String url)
            throws IOException, InterruptedException {
        var current = url;
        for (int hop = 0; hop < 5; hop++) {
            val response = browser.send(HttpRequest.newBuilder(URI.create(current)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            val location = response.headers().firstValue("Location");
            if (response.statusCode() / 100 != 3 || location.isEmpty()
                    || !location.get().startsWith(KEYCLOAK_INTERNAL_URL)) {
                return response;
            }
            current = toHostUrl(keycloak, location.get());
        }
        throw new IllegalStateException("Too many redirects from Keycloak.");
    }

    private static String toHostUrl(KeycloakContainer keycloak, String url) {
        val hostBase = keycloak.getAuthServerUrl().replaceAll("/$", "");
        return url.startsWith(KEYCLOAK_INTERNAL_URL) ? hostBase + url.substring(KEYCLOAK_INTERNAL_URL.length()) : url;
    }

    private static HttpRequest.BodyPublisher form(Map<String, String> fields) {
        return HttpRequest.BodyPublishers.ofString(formString(fields));
    }

    private static String formString(Map<String, String> fields) {
        return fields.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    private static String queryParameter(String url, String name) {
        val query = URI.create(url).getRawQuery();
        for (val pair : query.split("&")) {
            val keyValue = pair.split("=", 2);
            if (keyValue[0].equals(name) && keyValue.length == 2) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Query parameter '" + name + "' missing in " + url + ".");
    }

    private record Pkce(String verifier, String challenge) {
        static Pkce create() throws NoSuchAlgorithmException {
            val bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            val verifier  = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            val digest    = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return new Pkce(verifier, challenge);
        }
    }

}
