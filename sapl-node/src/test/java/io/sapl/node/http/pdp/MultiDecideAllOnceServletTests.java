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
package io.sapl.node.http.pdp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthHandler.HttpAuthResult;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultiDecideAllOnceServlet")
class MultiDecideAllOnceServletTests {

    @Mock
    private BlockingPolicyDecisionPoint pdp;

    @Mock
    private HttpAuthHandler authHandler;

    @Mock
    private HttpServletRequest oversizedRequest;

    private final JsonMapper mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Test
    @DisplayName("a chunked over-limit body is rejected with 413 and the PDP is never consulted")
    void whenBodyExceedsLimitThenContentTooLargeAndPdpNotCalled() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        when(oversizedRequest.getInputStream()).thenReturn(new TooLargeInputStream());
        val response = new MockHttpServletResponse();

        new MultiDecideAllOnceServlet(pdp, authHandler, mapper).handlePost(oversizedRequest, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verifyNoInteractions(pdp);
    }

    @Test
    @DisplayName("a JSON literal null body is rejected with 400 and the PDP is never consulted")
    void whenSubscriptionBodyIsJsonNullThenBadRequestAndPdpNotCalled() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        val request = new MockHttpServletRequest();
        request.setContent("null".getBytes(UTF_8));
        val response = new MockHttpServletResponse();

        new MultiDecideAllOnceServlet(pdp, authHandler, mapper).handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(pdp);
    }

    @Test
    @DisplayName("a multi-subscription above the configured entry limit is rejected with 400 and the PDP is never consulted")
    void whenMultiSubscriptionCountExceedsLimitThenBadRequestAndPdpNotCalled() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        val request = new MockHttpServletRequest();
        request.setContent("""
                {
                  "sub1": { "subject": "alice", "action": "read", "resource": "doc1" },
                  "sub2": { "subject": "bob", "action": "write", "resource": "doc2" }
                }
                """.getBytes(UTF_8));
        val response = new MockHttpServletResponse();

        new MultiDecideAllOnceServlet(pdp, authHandler, mapper, 1).handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(pdp);
    }

    @Test
    @DisplayName("a PDP runtime failure does not leak a 500 but fails closed to a 200 INDETERMINATE body")
    void whenPdpEvaluationThrowsThenOkWithIndeterminateBody() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default", null));
        when(pdp.decideAll(any(), any())).thenThrow(new IllegalStateException("evaluation exploded"));
        val request = new MockHttpServletRequest();
        request.setContent("{}".getBytes(UTF_8));
        val response = new MockHttpServletResponse();

        new MultiDecideAllOnceServlet(pdp, authHandler, mapper).handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        val decision = mapper.readValue(response.getContentAsByteArray(), MultiAuthorizationDecision.class);
        assertThat(decision.getDecisionType("")).isEqualTo(Decision.INDETERMINATE);
    }
}
