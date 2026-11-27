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
package io.sapl.attributes.libraries;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.test.stream.StreamAssertions;
import io.sapl.attributes.http.BlockingWebClient;
import lombok.val;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HTTP PIP trust selection on TLS connections")
class HttpPemTrustTests {
    private static final HeldCertificate ROOT    = new HeldCertificate.Builder().certificateAuthority(1).build();
    private static final HeldCertificate OTHER   = new HeldCertificate.Builder().certificateAuthority(1).build();
    private static final Duration        TIMEOUT = Duration.ofSeconds(10);

    private final MockWebServer              server = new MockWebServer();
    private final HttpPolicyInformationPoint pip    = new HttpPolicyInformationPoint(
            BlockingWebClient.withDefaults(JsonMapper.builder().build()));
    private String                           url;

    @BeforeEach
    void startServer() throws IOException {
        val leaf = new HeldCertificate.Builder().addSubjectAlternativeName("localhost").signedBy(ROOT).build();
        val tls  = new HandshakeCertificates.Builder().heldCertificate(leaf, ROOT.certificate()).build();
        server.setProtocols(List.of(Protocol.HTTP_1_1));
        server.useHttps(tls.sslSocketFactory(), false);
        server.start();
        url = "https://localhost:" + server.getPort();
    }

    @AfterEach
    void closeServer() throws IOException {
        server.close();
    }

    @Test
    @DisplayName("a rotated or removed CA cannot reuse an established HTTPS connection")
    void whenTrustRotatesThenEachRequestUsesItsOwnTrust() {
        server.enqueue(new MockResponse().setBody("1"));
        server.enqueue(new MockResponse().setBody("2"));
        val settings = request(url);
        val trusted  = context(url, ROOT.certificatePem());
        try (val stream = pip.get(trusted, settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(TIMEOUT)
                    .awaitsNext(v -> assertThat(v).isEqualTo(Value.of(1)));
        }
        assertError(context(url, OTHER.certificatePem()), settings);
        assertError(emptyContext(), settings);
        try (val stream = pip.get(trusted, settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(TIMEOUT)
                    .awaitsNext(v -> assertThat(v).isEqualTo(Value.of(2)));
        }
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "SSE", "WSS" })
    @DisplayName("the selected private CA authenticates the streaming endpoint")
    void whenStreamingWithPrivateRootThenMessageArrives(String protocol) {
        val websocket = "WSS".equals(protocol);
        val endpoint  = websocket ? url.replace("https:", "wss:") : url;
        if (websocket) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket socket, Response response) {
                    socket.send("17");
                    socket.close(1000, "done");
                }
            }));
        } else {
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: 17\n\n"));
        }
        val settings = ObjectValue.builder().put("baseUrl", Value.of(endpoint)).put("secretsKey", Value.of("svc"))
                .put("accept", Value.of("text/event-stream")).build();
        val ctx      = context(endpoint, ROOT.certificatePem());
        try (val stream = websocket ? pip.websocket(ctx, settings) : pip.get(ctx, settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(TIMEOUT)
                    .awaitsNext(v -> assertThat(v).isEqualTo(Value.of(17)));
        }
    }

    @Test
    @DisplayName("a CA-only entry is bound to its destination and still verifies hostnames")
    void whenDestinationOrHostnameDiffersThenRejectRequest() {
        assertError(context(url + "/private", ROOT.certificatePem()), request(url + "/public"));
        val wrongHost = url.replace("localhost", "127.0.0.1");
        assertError(context(wrongHost, ROOT.certificatePem()), request(wrongHost));
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("a policy or subscription cannot install its own trust anchor")
    void whenOnlyUntrustedSourcesSupplyRootThenHandshakeFails() {
        server.enqueue(new MockResponse().setBody("1"));
        val legitimate          = context(url, ROOT.certificatePem());
        val applicationSupplied = legitimate.variables();
        val noService           = ObjectValue.builder()
                .put("tlsTrustProfiles", applicationSupplied.get("tlsTrustProfiles")).build();
        val subscription        = new AttributeAccessContext(noService, Value.EMPTY_OBJECT, applicationSupplied);
        val policy              = ObjectValue.builder().putAll(request(url)).put("trustProfile", Value.of("private"))
                .put("trustedCaPem", Value.of(ROOT.certificatePem())).build();
        assertError(subscription, policy);
        val proposedSecret = namedSecret(ObjectValue.builder().put("trustedCaPem", Value.of(ROOT.certificatePem()))
                .put("trustProfile", Value.of("private")).put("allowedBaseUrls", Value.ofArray(Value.of(url))).build());
        assertError(new AttributeAccessContext(noService, proposedSecret, proposedSecret), policy);
        assertThat(server.getRequestCount()).isZero();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({ "http,application/json", "http,text/event-stream", "ws,application/json" })
    @DisplayName("explicit custom trust requires encrypted transport even for an allowed address")
    void whenTrustConfiguredForPlaintextThenFailBeforeConnecting(String scheme, String accept) {
        val endpoint = url.replace("https:", scheme + ":");
        val ctx      = context(endpoint, ROOT.certificatePem());
        val settings = ObjectValue.builder().putAll(request(endpoint)).put("accept", Value.of(accept)).build();
        try (val stream = "ws".equals(scheme) ? pip.websocket(ctx, settings) : pip.get(ctx, settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(TIMEOUT)
                    .awaitsNext(v -> assertThat(v).isInstanceOfSatisfying(ErrorValue.class,
                            error -> assertThat(error.message()).contains("require HTTPS or WSS")));
        }
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("a request path remains within the selected CA's permitted scope")
    void whenAssembledDestinationLeavesPermittedPathThenNoExchange() {
        val base     = url + "/private";
        val settings = ObjectValue.builder().put("baseUrl", Value.of(base)).put("path", Value.of("/../public"))
                .put("secretsKey", Value.of("svc")).build();
        assertError(context(base, ROOT.certificatePem()), settings);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("an allowed assembled URL receives both credential headers over the service's trusted connection")
    void whenFinalDestinationIsPermittedThenBothHeadersReachTheServer() throws Exception {
        val configured   = context(url + "/api", ROOT.certificatePem());
        val operator     = credential("X-Operator", url + "/api/team");
        val subscription = credential("X-Subscription", url + "/api/team/read");
        val ctx          = new AttributeAccessContext(configured.variables(), operator, subscription);
        server.enqueue(new MockResponse().setBody("23"));
        val allowed = ObjectValue.builder().putAll(request(url)).put("path", Value.of("/api/team/read?limit=1"))
                .build();
        try (val stream = pip.get(ctx, allowed)) {
            StreamAssertions.assertThat(stream).withinTimeout(TIMEOUT)
                    .awaitsNext(v -> assertThat(v).isEqualTo(Value.of(23)));
        }
        val received = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath()).isEqualTo("/api/team/read?limit=1");
        assertThat(received.getHeader("X-Operator")).isEqualTo("token");
        assertThat(received.getHeader("X-Subscription")).isEqualTo("token");
        assertThat(server.getRequestCount()).isOne();
    }

    @ParameterizedTest(name = "service {0}, operator credential {1}, subscription credential {2}")
    @CsvSource({ "/other,/read,/read", "/read,/other,/read", "/read,/read,/other", "/read?view=full,/read,/read",
            "/read,/read#section,/read", "/read,/read,/read?view=full" })
    @DisplayName("each allowlist can independently refuse the final URL")
    void whenOneSourceRefusesThenNoRequest(String servicePath, String operatorPath, String subscriptionPath) {
        val configured = context(url + servicePath, ROOT.certificatePem());
        val ctx        = new AttributeAccessContext(configured.variables(),
                credential("X-Operator", url + operatorPath), credential("X-Subscription", url + subscriptionPath));
        server.enqueue(new MockResponse().setBody("1"));
        val settings = ObjectValue.builder().putAll(request(url)).put("path", Value.of("/read")).build();
        assertError(ctx, settings);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("a named service without private trust still restricts the destination")
    void whenServiceUsesPlatformTrustThenEnforceItsAllowlist() {
        val service   = ObjectValue.builder().put("allowedBaseUrls", Value.ofArray(Value.of(url + "/scope"))).build();
        val variables = ObjectValue.builder().put("http", httpServices(service)).build();
        try (val stream = pip.get(new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT),
                request(url))) {
            StreamAssertions.assertThat(stream).withinTimeout(TIMEOUT)
                    .awaitsNext(v -> assertThat(v).isInstanceOfSatisfying(ErrorValue.class,
                            error -> assertThat(error.message()).contains("destination")));
        }
        assertThat(server.getRequestCount()).isZero();
    }

    private static ObjectValue credential(String header, String prefix) {
        return namedSecret(ObjectValue.builder().put("allowedBaseUrls", Value.ofArray(Value.of(prefix)))
                .put("headers", ObjectValue.builder().put(header, Value.of("token")).build()).build());
    }

    private static ObjectValue namedSecret(ObjectValue entry) {
        return ObjectValue.builder().put("http", ObjectValue.builder().put("svc", entry).build()).build();
    }

    private static ObjectValue httpServices(ObjectValue service) {
        return ObjectValue.builder().put("services", ObjectValue.builder().put("svc", service).build()).build();
    }

    private void assertError(AttributeAccessContext ctx, ObjectValue settings) {
        try (val stream = pip.get(ctx, settings)) {
            StreamAssertions.assertThat(stream).withinTimeout(TIMEOUT)
                    .awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class));
        }
    }

    private static ObjectValue request(String url) {
        return ObjectValue.builder().put("baseUrl", Value.of(url)).put("secretsKey", Value.of("svc")).build();
    }

    private static AttributeAccessContext context(String allowedUrl, String pem) {
        val profile   = ObjectValue.builder().put("trustedCaPem", Value.of(pem)).build();
        val service   = ObjectValue.builder().put("allowedBaseUrls", Value.ofArray(Value.of(allowedUrl)))
                .put("trustProfile", Value.of("private")).build();
        val variables = ObjectValue.builder().put("http", httpServices(service))
                .put("tlsTrustProfiles", ObjectValue.builder().put("private", profile).build()).build();
        return new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    }

    private static AttributeAccessContext emptyContext() {
        return new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
    }
}
