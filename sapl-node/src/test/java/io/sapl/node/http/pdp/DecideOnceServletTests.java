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
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthHandler.HttpAuthResult;
import io.sapl.node.auth.http.HttpAuthenticationException;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("DecideOnceServlet")
class DecideOnceServletTests {

    @Mock
    private BlockingPolicyDecisionPoint pdp;

    @Mock
    private HttpAuthHandler authHandler;

    @Mock
    private HttpServletRequest oversizedRequest;

    private final JsonMapper mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Test
    @DisplayName("malformed subscription JSON is rejected with 400 and the PDP is never consulted")
    void whenSubscriptionJsonIsMalformedThenBadRequestAndPdpNotCalled() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default"));
        val request = new MockHttpServletRequest();
        request.setContent("{ \"subject\": ".getBytes(UTF_8));
        val response = new MockHttpServletResponse();

        new DecideOnceServlet(pdp, authHandler, mapper).handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(pdp);
    }

    @Test
    @DisplayName("a failed authentication is rejected with 401 and the PDP is never consulted")
    void whenAuthenticationFailsThenUnauthorizedAndPdpNotCalled() throws Exception {
        when(authHandler.authenticate(any())).thenThrow(new HttpAuthenticationException("no credentials"));
        val request  = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();

        new DecideOnceServlet(pdp, authHandler, mapper).handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(pdp);
    }

    @Test
    @DisplayName("a chunked over-limit body is rejected with 413 and the PDP is never consulted")
    void whenBodyExceedsLimitThenContentTooLargeAndPdpNotCalled() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default"));
        when(oversizedRequest.getInputStream()).thenReturn(new TooLargeInputStream());
        val response = new MockHttpServletResponse();

        new DecideOnceServlet(pdp, authHandler, mapper).handlePost(oversizedRequest, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verifyNoInteractions(pdp);
    }

    @Test
    @DisplayName("a PDP failure fails closed: SC_OK with an INDETERMINATE decision and no stack leak")
    void whenPdpThrowsThenFailsClosedToIndeterminate() throws Exception {
        when(authHandler.authenticate(any())).thenReturn(new HttpAuthResult("default"));
        when(pdp.decideOnce(any(), any())).thenThrow(new RuntimeException("boom"));
        val request = new MockHttpServletRequest();
        request.setContent("{\"subject\":\"alice\",\"action\":\"read\",\"resource\":\"doc\"}".getBytes(UTF_8));
        val response = new MockHttpServletResponse();

        new DecideOnceServlet(pdp, authHandler, mapper).handlePost(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentAsString()).contains("INDETERMINATE");
    }
}
