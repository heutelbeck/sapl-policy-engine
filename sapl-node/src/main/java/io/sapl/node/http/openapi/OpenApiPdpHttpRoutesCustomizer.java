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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Merges the hand-written OpenAPI spec for the bypass-Spring PDP HTTP routes
 * into the springdoc-generated document so Scalar renders a single, flat
 * API reference instead of switching between multiple source tabs.
 * <p>
 * The bypass servlets at {@code /api/pdp/*} live outside the
 * {@code DispatcherServlet} and therefore cannot be auto-introspected by
 * springdoc. The static resource at
 * {@code /static/openapi/pdp-http.yaml} is the source of truth; this
 * customizer parses it once at bean creation and folds its paths, schemas,
 * response definitions and tags into the live OpenAPI document on each
 * {@code /v3/api-docs} request.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class OpenApiPdpHttpRoutesCustomizer {

    private static final String PDP_HTTP_SPEC = "static/openapi/pdp-http.yaml";

    @Bean
    OpenApiCustomizer pdpHttpRoutesCustomizer() {
        val pdpHttpApi = loadPdpHttpSpec();
        return openApi -> {
            mergePaths(openApi, pdpHttpApi);
            mergeComponents(openApi, pdpHttpApi);
            mergeTags(openApi, pdpHttpApi);
        };
    }

    private OpenAPI loadPdpHttpSpec() {
        try (InputStream in = new ClassPathResource(PDP_HTTP_SPEC).getInputStream()) {
            return Yaml31.mapper().readValue(in, OpenAPI.class);
        } catch (IOException e) {
            throw new IllegalStateException("Required classpath resource " + PDP_HTTP_SPEC
                    + " is missing from the deployment artifact. If this is a native image, verify that "
                    + "SaplNodeApplication.NativeResourceHints registers the resource pattern.", e);
        }
    }

    private static void mergePaths(OpenAPI target, OpenAPI source) {
        if (source.getPaths() == null) {
            return;
        }
        if (target.getPaths() == null) {
            target.setPaths(new Paths());
        }
        source.getPaths().forEach(target.getPaths()::addPathItem);
    }

    private static void mergeComponents(OpenAPI target, OpenAPI source) {
        val sourceComponents = source.getComponents();
        if (sourceComponents == null) {
            return;
        }
        if (target.getComponents() == null) {
            target.setComponents(new Components());
        }
        val targetComponents = target.getComponents();
        if (sourceComponents.getSchemas() != null) {
            sourceComponents.getSchemas().forEach(targetComponents::addSchemas);
        }
        if (sourceComponents.getResponses() != null) {
            sourceComponents.getResponses().forEach(targetComponents::addResponses);
        }
    }

    /**
     * Tag order drives section order in the Scalar sidebar. PDP tags from
     * the YAML go first, then anything springdoc already collected (OpenID
     * controller, actuator endpoints) in its existing order.
     */
    private static void mergeTags(OpenAPI target, OpenAPI source) {
        val ordered = new ArrayList<Tag>();
        if (source.getTags() != null) {
            ordered.addAll(source.getTags());
        }
        val seen = new LinkedHashMap<String, Boolean>();
        ordered.forEach(t -> seen.put(t.getName(), Boolean.TRUE));
        if (target.getTags() != null) {
            target.getTags().stream().filter(t -> !seen.containsKey(t.getName())).forEach(ordered::add);
        }
        target.setTags(ordered);
    }

}
