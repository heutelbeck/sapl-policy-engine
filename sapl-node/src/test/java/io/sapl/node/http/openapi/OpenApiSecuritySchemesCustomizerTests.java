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
package io.sapl.node.http.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.sun.net.httpserver.HttpServer;

import io.sapl.node.SaplNodeProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.val;

@DisplayName("OpenAPI security schemes for the Scalar Authorize panel")
class OpenApiSecuritySchemesCustomizerTests {

    private static final String CLIENT_ID_PROPERTY    = "io.sapl.node.scalar.oauth-client-id";
    private static final String DISCOVERY_PATH        = "/.well-known/openid-configuration";
    private static final String ISSUER_PROPERTY       = "spring.security.oauth2.resourceserver.jwt.issuer-uri";
    private static final String REDIRECT_URI_PROPERTY = "io.sapl.node.scalar.oauth-redirect-uri";
    private static final String SCALAR_CLIENT_ID      = "x-scalar-client-id";
    private static final String SCALAR_REDIRECT_URI   = "x-scalar-redirect-uri";

    private static final String VALID_DISCOVERY = """
            {
              "issuer": "{issuer}",
              "authorization_endpoint": "{issuer}/auth",
              "token_endpoint": "{issuer}/token"
            }
            """;

    private MockIssuer issuer;

    @BeforeEach
    void startIssuer() throws IOException {
        issuer = new MockIssuer();
    }

    @AfterEach
    void stopIssuer() {
        issuer.close();
    }

    @Nested
    @DisplayName("OAuth2 settings handed to Scalar")
    class ScalarOauthSettings {

        @Test
        @DisplayName("configured client id is passed to Scalar so the Authorize dialog is prefilled")
        void whenClientIdConfiguredThenFlowCarriesScalarClientId() {
            issuer.respondWithValidDiscovery();

            assertAuthorizationCodeFlow(oauthContext().withPropertyValues(CLIENT_ID_PROPERTY + "=sapl-node-scalar"),
                    flow -> assertThat(flow.getExtensions()).containsEntry(SCALAR_CLIENT_ID, "sapl-node-scalar")
                            .containsEntry(SCALAR_REDIRECT_URI, "/scalar"));
        }

        @Test
        @DisplayName("without a configured client id Scalar receives no client id and the user enters one")
        void whenNoClientIdConfiguredThenFlowHasNoScalarClientId() {
            issuer.respondWithValidDiscovery();

            assertAuthorizationCodeFlow(oauthContext(), flow -> assertThat(flow.getExtensions())
                    .doesNotContainKey(SCALAR_CLIENT_ID).containsEntry(SCALAR_REDIRECT_URI, "/scalar"));
        }

        @Test
        @DisplayName("configured redirect URI is passed to Scalar")
        void whenRedirectUriConfiguredThenFlowCarriesIt() {
            issuer.respondWithValidDiscovery();

            assertAuthorizationCodeFlow(
                    oauthContext().withPropertyValues(REDIRECT_URI_PROPERTY + "=https://pdp.example.com/scalar"),
                    flow -> assertThat(flow.getExtensions()).containsEntry(SCALAR_REDIRECT_URI,
                            "https://pdp.example.com/scalar"));
        }

        @Test
        @DisplayName("authorize and token URLs come from the issuer's discovery document")
        void whenDiscoverySucceedsThenFlowUsesDiscoveredEndpoints() {
            issuer.respondWithValidDiscovery();

            assertAuthorizationCodeFlow(oauthContext(),
                    flow -> assertThat(flow).returns(issuer.uri() + "/auth", OAuthFlow::getAuthorizationUrl)
                            .returns(issuer.uri() + "/token", OAuthFlow::getTokenUrl));
        }
    }

    @Nested
    @DisplayName("Issuer failures")
    class IssuerFailures {

        static Stream<Arguments> brokenDiscoveryResponses() {
            return Stream.of(arguments("HTTP 500", 500, "{}"), arguments("HTTP 404", 404, "not found"),
                    arguments("invalid JSON", 200, "{ this is not json"), arguments("missing token endpoint", 200, """
                            { "authorization_endpoint": "https://idp.example.com/auth" }
                            """), arguments("missing authorization endpoint", 200, """
                            { "token_endpoint": "https://idp.example.com/token" }
                            """));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("brokenDiscoveryResponses")
        @DisplayName("a broken discovery response leaves the OAuth2 scheme out instead of failing the document")
        void whenDiscoveryResponseIsBrokenThenNoOauthScheme(String description, int status, String body) {
            issuer.respond(status, body);

            oauthContext().run(context -> {
                val openApi = customize(context.getBean(OpenApiCustomizer.class));
                assertThat(openApi.getComponents().getSecuritySchemes()).isNullOrEmpty();
                assertThat(openApi.getSecurity()).isEmpty();
            });
        }

        @Test
        @DisplayName("an unreachable issuer leaves the OAuth2 scheme out instead of failing the document")
        void whenIssuerUnreachableThenNoOauthScheme() {
            val unreachableIssuer = issuer.uri();
            issuer.close();

            oauthContext(unreachableIssuer).run(context -> {
                val openApi = customize(context.getBean(OpenApiCustomizer.class));
                assertThat(openApi.getComponents().getSecuritySchemes()).isNullOrEmpty();
            });
        }
    }

    @Nested
    @DisplayName("Recovery and caching")
    class RecoveryAndCaching {

        @Test
        @DisplayName("after an issuer outage the OAuth2 scheme appears once the issuer is back")
        void whenIssuerRecoversThenOauthSchemeAppears() {
            issuer.respond(503, "{}");

            oauthContext().run(context -> {
                val customizer   = context.getBean(OpenApiCustomizer.class);
                val duringOutage = customize(customizer);

                issuer.respondWithValidDiscovery();
                val afterRecovery = customize(customizer);

                assertThat(duringOutage.getComponents().getSecuritySchemes()).isNullOrEmpty();
                assertThat(afterRecovery.getComponents().getSecuritySchemes()).containsKey("oauth2");
            });
        }

        @Test
        @DisplayName("a successfully resolved scheme is reused without contacting the issuer again")
        void whenSchemeResolvedThenIssuerIsQueriedOnce() {
            issuer.respondWithValidDiscovery();

            oauthContext().run(context -> {
                val customizer = context.getBean(OpenApiCustomizer.class);
                customize(customizer);
                customize(customizer);
                customize(customizer);

                assertThat(issuer.requestCount()).isEqualTo(1);
            });
        }
    }

    private ApplicationContextRunner oauthContext() {
        return oauthContext(issuer.uri());
    }

    private static ApplicationContextRunner oauthContext(String issuerUri) {
        val properties = new SaplNodeProperties();
        properties.setAllowOauth2Auth(true);
        return new ApplicationContextRunner().withUserConfiguration(OpenApiSecuritySchemesCustomizer.class)
                .withBean(SaplNodeProperties.class, () -> properties)
                .withPropertyValues(ISSUER_PROPERTY + "=" + issuerUri);
    }

    private static void assertAuthorizationCodeFlow(ApplicationContextRunner runner, Consumer<OAuthFlow> requirements) {
        runner.run(context -> {
            val scheme = customize(context.getBean(OpenApiCustomizer.class)).getComponents().getSecuritySchemes()
                    .get("oauth2");
            assertThat(scheme).isNotNull().returns(SecurityScheme.Type.OAUTH2, SecurityScheme::getType)
                    .extracting(s -> s.getFlows().getAuthorizationCode()).isNotNull().satisfies(requirements);
        });
    }

    private static OpenAPI customize(OpenApiCustomizer customizer) {
        val openApi = new OpenAPI();
        customizer.customise(openApi);
        return openApi;
    }

    /**
     * Minimal OIDC issuer serving only the discovery document. Binds to an
     * ephemeral port so parallel test runs never collide.
     */
    private static final class MockIssuer implements AutoCloseable {

        private final HttpServer    server;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicBoolean closed   = new AtomicBoolean();
        private volatile int        status   = 200;
        private volatile String     body     = "{}";

        MockIssuer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(DISCOVERY_PATH, exchange -> {
                requests.incrementAndGet();
                val bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (val out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
        }

        void respond(int status, String body) {
            this.status = status;
            this.body   = body;
        }

        void respondWithValidDiscovery() {
            respond(200, VALID_DISCOVERY.replace("{issuer}", uri()));
        }

        String uri() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        int requestCount() {
            return requests.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                server.stop(0);
            }
        }
    }
}
