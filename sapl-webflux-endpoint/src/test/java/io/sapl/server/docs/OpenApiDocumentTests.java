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
package io.sapl.server.docs;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.api.tenant.ReactiveTenantResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@DisplayName("OpenAPI document exposes both API families and the SSE endpoints")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = OpenApiDocumentTests.TestApp.class)
@AutoConfigureWebTestClient
class OpenApiDocumentTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = { "io.sapl.server.openidauthzapi", "io.sapl.server.pdpcontroller",
            "io.sapl.server.docs" })
    static class TestApp {
    }

    @MockitoBean
    private ReactivePolicyDecisionPoint pdp;

    @MockitoBean
    private ReactiveTenantResolver tenantResolver;

    @Autowired
    private WebTestClient webClient;

    @Test
    void apiDocsEndpointReturnsValidOpenApiVersion3Document() {
        webClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().jsonPath("$.openapi")
                .value(o -> ((String) o).startsWith("3."));
    }

    @Test
    void apiDocsContainsOpenIdEvaluationPath() {
        webClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/access/v1/evaluation'].post").exists();
    }

    @Test
    void apiDocsContainsAllSaplNativePaths() {
        webClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/pdp/decide-once'].post").exists().jsonPath("$.paths['/api/pdp/decide'].post")
                .exists().jsonPath("$.paths['/api/pdp/multi-decide'].post").exists()
                .jsonPath("$.paths['/api/pdp/multi-decide-all'].post").exists()
                .jsonPath("$.paths['/api/pdp/multi-decide-all-once'].post").exists();
    }

    @Test
    void sseEndpointsDeclareTextEventStreamProduces() {
        webClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/pdp/decide'].post.responses.200.content['text/event-stream']").exists()
                .jsonPath("$.paths['/api/pdp/multi-decide'].post.responses.200.content['text/event-stream']").exists()
                .jsonPath("$.paths['/api/pdp/multi-decide-all'].post.responses.200.content['text/event-stream']")
                .exists();
    }

    @Test
    void apiDocsCarriesSaplOpenApiInfoTitleAndContact() {
        webClient.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().jsonPath("$.info.title")
                .isEqualTo("SAPL Policy Decision Point API").jsonPath("$.info.contact.email")
                .isEqualTo("dominic@heutelbeck.com");
    }

    @Test
    void scalarUiServedAtRoot() {
        webClient.get().uri("/").exchange().expectStatus().isOk().expectHeader().contentTypeCompatibleWith("text/html");
    }
}
