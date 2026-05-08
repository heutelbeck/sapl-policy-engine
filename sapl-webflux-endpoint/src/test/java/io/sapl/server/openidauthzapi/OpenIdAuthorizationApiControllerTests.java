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
package io.sapl.server.openidauthzapi;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.reactive.api.pdp.PolicyDecisionPoint;
import io.sapl.reactive.api.tenant.ReactiveTenantResolver;
import io.sapl.server.pdpcontroller.SaplJacksonAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("OpenID Authorization API controller")
@WebFluxTest(controllers = OpenIdAuthorizationApiController.class)
@ContextConfiguration(classes = { OpenIdAuthorizationApiController.class, SaplJacksonAutoConfiguration.class })
class OpenIdAuthorizationApiControllerTests {

    private static final String EVALUATION_PATH = "/access/v1/evaluation";

    private static final String VALID_REQUEST_BODY = """
            {
              "subject":  { "type": "user", "id": "alice@acme.com" },
              "action":   { "name": "can_read" },
              "resource": { "type": "book", "id": "42" }
            }
            """;

    @MockitoBean
    private PolicyDecisionPoint pdp;

    @MockitoBean
    private ReactiveTenantResolver tenantResolver;

    @Autowired
    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        when(tenantResolver.resolve()).thenReturn(Mono.just(PolicyDecisionPoint.DEFAULT_PDP_ID));
    }

    @Nested
    @DisplayName("decision verb mapping")
    class VerbMapping {

        @Test
        void permitReturnsDecisionTrue() {
            stubPdp(AuthorizationDecision.PERMIT);
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(true)
                    .jsonPath("$.context").doesNotExist();
        }

        @Test
        void denyReturnsDecisionFalse() {
            stubPdp(AuthorizationDecision.DENY);
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(false)
                    .jsonPath("$.context").doesNotExist();
        }

        @Test
        void notApplicableReturnsDecisionFalse() {
            stubPdp(AuthorizationDecision.NOT_APPLICABLE);
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(false)
                    .jsonPath("$.context").doesNotExist();
        }

        @Test
        void indeterminateReturnsDecisionFalseWithReasonAdmin() {
            stubPdp(AuthorizationDecision.INDETERMINATE);
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(false)
                    .jsonPath("$.context.reason_admin.en").exists();
        }

        @Test
        void suspendReturnsDecisionFalseWithReasonUserAndSaplMarker() {
            stubPdp(AuthorizationDecision.SUSPEND);
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(false)
                    .jsonPath("$.context.reason_user.en-403").exists().jsonPath("$.context.sapl.decision")
                    .isEqualTo("SUSPEND");
        }
    }

    @Nested
    @DisplayName("SAPL extensions in response context")
    class SaplExtensions {

        @Test
        void permitWithObligationsCarriesObligationsInContext() {
            stubPdp(new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED));
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(true)
                    .jsonPath("$.context.sapl.obligations").exists();
        }

        @Test
        void permitWithResourceTransformationCarriesResourceInContext() {
            stubPdp(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted")));
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(true)
                    .jsonPath("$.context.sapl.resource").exists();
        }
    }

    @Nested
    @DisplayName("X-Request-ID echo")
    class RequestIdEcho {

        @Test
        void requestIdHeaderEchoedInResponse() {
            stubPdp(AuthorizationDecision.PERMIT);
            webClient.post().uri(EVALUATION_PATH).contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-ID", "trace-abc-123").bodyValue(VALID_REQUEST_BODY).exchange().expectStatus()
                    .isOk().expectHeader().valueEquals("X-Request-ID", "trace-abc-123");
        }

        @Test
        void noRequestIdHeaderMeansNoEchoHeader() {
            stubPdp(AuthorizationDecision.PERMIT);
            postValidRequest().expectStatus().isOk().expectHeader().doesNotExist("X-Request-ID");
        }
    }

    @Nested
    @DisplayName("validation 400s")
    class Validation {

        @Test
        void missingSubjectReturnsBadRequest() {
            final var body = """
                    { "action": { "name": "can_read" }, "resource": { "type": "book", "id": "42" } }
                    """;
            postBody(body).expectStatus().isBadRequest();
        }

        @Test
        void missingActionNameReturnsBadRequest() {
            final var body = """
                    { "subject": { "type": "user", "id": "alice" },
                      "action":   { },
                      "resource": { "type": "book", "id": "42" } }
                    """;
            postBody(body).expectStatus().isBadRequest();
        }

        @Test
        void missingResourceIdReturnsBadRequest() {
            final var body = """
                    { "subject":  { "type": "user", "id": "alice" },
                      "action":   { "name": "can_read" },
                      "resource": { "type": "book" } }
                    """;
            postBody(body).expectStatus().isBadRequest();
        }

        @Test
        void malformedJsonReturnsBadRequest() {
            postBody("not json at all").expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        void pdpErrorReturnsIndeterminate() {
            when(pdp.decideOnce(any(AuthorizationSubscription.class), eq(PolicyDecisionPoint.DEFAULT_PDP_ID)))
                    .thenReturn(Mono.error(new RuntimeException("boom")));
            postValidRequest().expectStatus().isOk().expectBody().jsonPath("$.decision").isEqualTo(false)
                    .jsonPath("$.context.reason_admin.en").exists();
        }
    }

    private void stubPdp(AuthorizationDecision decision) {
        when(pdp.decideOnce(any(AuthorizationSubscription.class), eq(PolicyDecisionPoint.DEFAULT_PDP_ID)))
                .thenReturn(Mono.just(decision));
    }

    private WebTestClient.ResponseSpec postValidRequest() {
        return postBody(VALID_REQUEST_BODY);
    }

    private WebTestClient.ResponseSpec postBody(String body) {
        return webClient.post().uri(EVALUATION_PATH).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).bodyValue(body).exchange();
    }
}
