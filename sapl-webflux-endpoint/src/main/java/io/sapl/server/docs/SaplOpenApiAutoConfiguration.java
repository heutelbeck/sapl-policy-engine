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

import com.scalar.maven.webflux.ScalarWebFluxController;
import com.scalar.maven.webflux.SpringBootScalarProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Provides the OpenAPI document metadata for the SAPL PDP HTTP API. Active
 * whenever the swagger-models classes are on the classpath (i.e. springdoc
 * is in use).
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(SpringBootScalarProperties.class)
public class SaplOpenApiAutoConfiguration {

    private static final String API_TITLE       = "SAPL Policy Decision Point API";
    private static final String API_VERSION     = "1.0";
    private static final String API_DESCRIPTION = """
            HTTP API for the SAPL Policy Decision Point.

            Two endpoint families are exposed:

            * **SAPL native** at `/api/pdp/*` — the full SAPL surface: one-shot decisions, server-sent-event \
            streams, multi-decision boxcars, and SAPL's five-valued decision verb (PERMIT, DENY, SUSPEND, \
            INDETERMINATE, NOT_APPLICABLE) with obligations, advice, and resource transformation.
            * **OpenID Authorization API** at `/access/v1/evaluation` — interop binding per OpenID \
            Authorization API 1.0 with a boolean decision. SAPL detail is surfaced via the response \
            `context` for SAPL-aware clients.""";

    private static final String SAPL_NATIVE_TAG = "SAPL native";
    private static final String OPENID_TAG      = "OpenID Authorization API";

    /**
     * Explicitly registers the Scalar API Reference controller. Scalar ships its
     * own auto-configuration
     * (`com.scalar.maven.webflux.ScalarWebFluxAutoConfiguration`)
     * but its `@Bean` methods are not picked up under Spring Boot 4 (the autoconfig
     * is annotated `@Configuration` rather than `@AutoConfiguration` and the
     * `.imports`-based discovery treats the two differently in Boot 4). Registering
     * the controller here ensures it is wired with the SAPL defaults.
     */
    @Bean
    @ConditionalOnClass(ScalarWebFluxController.class)
    @ConditionalOnMissingBean
    public ScalarWebFluxController scalarWebFluxController() {
        return new ScalarWebFluxController();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI saplOpenApi() {
        return new OpenAPI().components(new Components())
                .info(new Info().title(API_TITLE).version(API_VERSION).description(API_DESCRIPTION)
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
                        .contact(new Contact().name("Dominic Heutelbeck").email("dominic@heutelbeck.com")
                                .url("https://sapl.io")))
                .tags(List.of(
                        new Tag().name(SAPL_NATIVE_TAG)
                                .description("Full SAPL PDP surface: one-shot, streaming, and multi-decision."),
                        new Tag().name(OPENID_TAG).description(
                                "OpenID Authorization API 1.0 binding. Boolean decision, single evaluation.")));
    }
}
