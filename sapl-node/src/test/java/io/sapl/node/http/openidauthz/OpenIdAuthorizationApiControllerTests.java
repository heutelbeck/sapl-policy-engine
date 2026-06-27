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
package io.sapl.node.http.openidauthz;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import io.sapl.spring.pdp.embedded.PdpObjectMapperAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OpenID Authorization API controller")
@WebMvcTest(controllers = OpenIdAuthorizationApiController.class)
@ContextConfiguration(classes = { OpenIdAuthorizationApiController.class, OpenIdAuthorizationApiExceptionHandler.class,
        PdpObjectMapperAutoConfiguration.class })
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
    private BlockingPolicyDecisionPoint pdp;

    @MockitoBean
    private BlockingTenantResolver tenantResolver;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(tenantResolver.resolve()).thenReturn(StreamingPolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    @Nested
    @DisplayName("decision verb mapping")
    class VerbMapping {

        @Test
        void permitReturnsDecisionTrueWithSaplMarker() throws Exception {
            stubPdp(AuthorizationDecision.PERMIT);
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(true))
                    .andExpect(jsonPath("$.context.sapl.decision").value("PERMIT"));
        }

        @Test
        void denyReturnsDecisionFalseWithReasonUserAndSaplMarker() throws Exception {
            stubPdp(AuthorizationDecision.DENY);
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(false))
                    .andExpect(jsonPath("$.context.reason_user.en-403").exists())
                    .andExpect(jsonPath("$.context.sapl.decision").value("DENY"));
        }

        @Test
        void notApplicableReturnsDecisionFalseWithReasonUserAndSaplMarker() throws Exception {
            stubPdp(AuthorizationDecision.NOT_APPLICABLE);
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(false))
                    .andExpect(jsonPath("$.context.reason_user.en-403").exists())
                    .andExpect(jsonPath("$.context.sapl.decision").value("NOT_APPLICABLE"));
        }

        @Test
        void indeterminateReturnsDecisionFalseWithReasonAdmin() throws Exception {
            stubPdp(AuthorizationDecision.INDETERMINATE);
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(false))
                    .andExpect(jsonPath("$.context.reason_admin.en").exists());
        }

        @Test
        void suspendReturnsDecisionFalseWithReasonUserAndSaplMarker() throws Exception {
            stubPdp(AuthorizationDecision.SUSPEND);
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(false))
                    .andExpect(jsonPath("$.context.reason_user.en-403").exists())
                    .andExpect(jsonPath("$.context.sapl.decision").value("SUSPEND"));
        }
    }

    @Nested
    @DisplayName("SAPL extensions in response context")
    class SaplExtensions {

        @Test
        void permitWithObligationsMapsToFalseAndCarriesObligationsInContext() throws Exception {
            stubPdp(new AuthorizationDecision(Decision.PERMIT, Value.ofArray(Value.of("notify-admin")),
                    Value.EMPTY_ARRAY, Value.UNDEFINED));
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(false))
                    .andExpect(jsonPath("$.context.reason_admin.en").exists())
                    .andExpect(jsonPath("$.context.sapl.decision").value("PERMIT"))
                    .andExpect(jsonPath("$.context.sapl.obligations").exists());
        }

        @Test
        void permitWithResourceTransformationMapsToFalseAndCarriesResourceInContext() throws Exception {
            stubPdp(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.of("redacted")));
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(false))
                    .andExpect(jsonPath("$.context.reason_admin.en").exists())
                    .andExpect(jsonPath("$.context.sapl.decision").value("PERMIT"))
                    .andExpect(jsonPath("$.context.sapl.resource").exists());
        }
    }

    @Nested
    @DisplayName("X-Request-ID echo")
    class RequestIdEcho {

        @Test
        void requestIdHeaderEchoedInResponse() throws Exception {
            stubPdp(AuthorizationDecision.PERMIT);
            mockMvc.perform(post(EVALUATION_PATH).contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-ID", "trace-abc-123").content(VALID_REQUEST_BODY)).andExpect(status().isOk())
                    .andExpect(header().string("X-Request-ID", "trace-abc-123"));
        }

        @Test
        void noRequestIdHeaderMeansNoEchoHeader() throws Exception {
            stubPdp(AuthorizationDecision.PERMIT);
            postValidRequest().andExpect(status().isOk()).andExpect(header().doesNotExist("X-Request-ID"));
        }
    }

    @Nested
    @DisplayName("validation 400s")
    class Validation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidBodies")
        void invalidBodyReturnsBadRequest(String description, String body) throws Exception {
            postBody(body).andExpect(status().isBadRequest());
        }

        static Stream<Arguments> invalidBodies() {
            return Stream.of(arguments("missing subject", """
                    { "action": { "name": "can_read" }, "resource": { "type": "book", "id": "42" } }
                    """), arguments("missing action name", """
                    { "subject": { "type": "user", "id": "alice" },
                      "action":   { },
                      "resource": { "type": "book", "id": "42" } }
                    """), arguments("missing resource id", """
                    { "subject":  { "type": "user", "id": "alice" },
                      "action":   { "name": "can_read" },
                      "resource": { "type": "book" } }
                    """), arguments("malformed JSON", "not json at all"));
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandling {

        @Test
        void pdpErrorReturnsIndeterminate() throws Exception {
            when(pdp.decideOnce(any(AuthorizationSubscription.class), eq(StreamingPolicyDecisionPoint.DEFAULT_PDP_ID)))
                    .thenThrow(new RuntimeException("boom"));
            postValidRequest().andExpect(status().isOk()).andExpect(jsonPath("$.decision").value(false))
                    .andExpect(jsonPath("$.context.reason_admin.en").exists());
        }
    }

    private void stubPdp(AuthorizationDecision decision) {
        when(pdp.decideOnce(any(AuthorizationSubscription.class), eq(StreamingPolicyDecisionPoint.DEFAULT_PDP_ID)))
                .thenReturn(decision);
    }

    private ResultActions postValidRequest() throws Exception {
        return postBody(VALID_REQUEST_BODY);
    }

    private ResultActions postBody(String body) throws Exception {
        return mockMvc.perform(post(EVALUATION_PATH).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).content(body));
    }
}
