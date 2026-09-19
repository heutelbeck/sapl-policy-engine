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
package io.sapl.attributeapi.attributes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpHeaders;

import io.lettuce.core.RedisConnectionException;
import io.sapl.attributeapi.attributes.backend.AttributeBackendUnavailableException;
import io.sapl.attributeapi.attributes.service.AttributeApiService;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;

@WebMvcTest(AttributeApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "io.sapl.attribute-api.enabled=true")
class AttributeApiContollerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttributeApiService service;

    @MockitoBean
    private BlockingTenantResolver tenantResolver;

    @Test
    @DisplayName("When the backend is missing then a GET will return a 503 - service unavailble with a generic message")
    void whenBackendUnavailableThenHttpClientReceivesGenericmessage() throws Exception {
        var interrupted = new RedisConnectionException("Connection refused to redis");
        when(service.get(isNull(), eq("sapl.test"), any(), any())).thenThrow(
                new AttributeBackendUnavailableException("The service is currently unavailable", interrupted));

        MvcResult result = mockMvc.perform(get("/api/attributes/sapl.test")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5")).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).isEqualTo("The service is currently unavailable").doesNotContain("Redis", "Connection refused",
                "RedisConnectionException");
    }
}
