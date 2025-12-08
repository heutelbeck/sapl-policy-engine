/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.pdpcontroller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.pdp.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Import(PolicyDecisionPoint.class)
@ContextConfiguration(classes = { PDPController.class, SaplJacksonAutoConfiguration.class })
@WebFluxTest(controllers = PDPController.class, properties = "io.sapl.server.keep-alive=10")
class PDPControllerWithKeepaliveTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @MockitoBean
    private PolicyDecisionPoint pdp;

    @Autowired
    private WebTestClient webClient;

    @Test
    void decideWithValidBody() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY,
                AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");

        final var result = webClient.post().uri("/api/pdp/decide").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
                .returnResult(AuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.DENY,
                AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE).thenCancel().verify();

        verify(pdp, times(1)).decide(subscription);
    }

    @Test
    void decideOnceValidBody() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY,
                AuthorizationDecision.PERMIT, AuthorizationDecision.INDETERMINATE));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");

        final var result = webClient.post().uri("/api/pdp/decide-once").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
                .returnResult(AuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.DENY).thenCancel().verify();

        verify(pdp, times(1)).decide(subscription);
    }

    @Test
    void decideWithValidProcessingError() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");

        final var result = webClient.post().uri("/api/pdp/decide").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
                .returnResult(AuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.INDETERMINATE).thenCancel()
                .verify();

        verify(pdp, times(1)).decide(subscription);
    }

    @Test
    void decideOnceWithValidProcessingError() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");

        final var result = webClient.post().uri("/api/pdp/decide-once").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isOk()
                .returnResult(AuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(AuthorizationDecision.INDETERMINATE).thenCancel()
                .verify();

        verify(pdp, times(1)).decide(subscription);
    }

    @Test
    void decideWithInvalidBody() {
        webClient.post().uri("/api/pdp/decide").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue("INVALID BODY")).exchange().expectStatus().isBadRequest();
    }

    @Test
    void subscribeToMultiDecisions() {
        when(pdp.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.just(
                IdentifiableAuthorizationDecision.INDETERMINATE, IdentifiableAuthorizationDecision.INDETERMINATE,
                IdentifiableAuthorizationDecision.INDETERMINATE));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = webClient.post().uri("/api/pdp/multi-decide").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
                .returnResult(IdentifiableAuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(IdentifiableAuthorizationDecision.INDETERMINATE,
                IdentifiableAuthorizationDecision.INDETERMINATE, IdentifiableAuthorizationDecision.INDETERMINATE)
                .thenCancel().verify();

        verify(pdp, times(1)).decide(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiDecisionsProcessingError() {
        when(pdp.decide(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = webClient.post().uri("/api/pdp/multi-decide").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
                .returnResult(IdentifiableAuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(IdentifiableAuthorizationDecision.INDETERMINATE)
                .thenCancel().verify();

        verify(pdp, times(1)).decide(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiDecisionsInvalidBody() {
        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        webClient.post().uri("/api/pdp/multi-decide").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isBadRequest()
                .returnResult(IdentifiableAuthorizationDecision.class);
    }

    @Test
    void subscribeToMultiAllDecisions() {
        when(pdp.decideAll(any(MultiAuthorizationSubscription.class)))
                .thenReturn(Flux.just(MultiAuthorizationDecision.indeterminate(),
                        MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = webClient.post().uri("/api/pdp/multi-decide-all").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
                .returnResult(MultiAuthorizationDecision.class);

        StepVerifier
                .create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate(),
                        MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate())
                .thenCancel().verify();

        verify(pdp, times(1)).decideAll(multiAuthzSubscription);
    }

    @Test
    void oneMultiAllDecisions() {
        when(pdp.decideAll(any(MultiAuthorizationSubscription.class)))
                .thenReturn(Flux.just(MultiAuthorizationDecision.indeterminate(),
                        MultiAuthorizationDecision.indeterminate(), MultiAuthorizationDecision.indeterminate()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = webClient.post().uri("/api/pdp/multi-decide-all-once")
                .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
                .returnResult(MultiAuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate())
                .thenCancel().verify();

        verify(pdp, times(1)).decideAll(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiAllDecisionsProcessingError() {
        when(pdp.decideAll(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = webClient.post().uri("/api/pdp/multi-decide-all").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
                .returnResult(MultiAuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate())
                .thenCancel().verify();

        verify(pdp, times(1)).decideAll(multiAuthzSubscription);
    }

    @Test
    void oneMultiAllDecisionsProcessingError() {
        when(pdp.decideAll(any(MultiAuthorizationSubscription.class))).thenReturn(Flux.error(new RuntimeException()));

        final var multiAuthzSubscription = new MultiAuthorizationSubscription().addAuthorizationSubscription("id1",
                JSON.textNode("subject"), JSON.textNode("action1"), JSON.textNode("resource"))
                .addAuthorizationSubscription("id2", JSON.textNode("subject"), JSON.textNode("action2"),
                        JSON.textNode("other resource"));

        final var result = webClient.post().uri("/api/pdp/multi-decide-all-once")
                .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(BodyInserters.fromValue(multiAuthzSubscription)).exchange().expectStatus().isOk()
                .returnResult(MultiAuthorizationDecision.class);

        StepVerifier.create(result.getResponseBody()).expectNext(MultiAuthorizationDecision.indeterminate())
                .thenCancel().verify();

        verify(pdp, times(1)).decideAll(multiAuthzSubscription);
    }

    @Test
    void subscribeToMultiDecisionsAllInvalidBody() {
        final var subscription = AuthorizationSubscription.of("subject", "action", "resource");
        webClient.post().uri("/api/pdp/multi-decide-all").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_NDJSON_VALUE)
                .body(BodyInserters.fromValue(subscription)).exchange().expectStatus().isBadRequest()
                .returnResult(IdentifiableAuthorizationDecision.class);
    }

}
