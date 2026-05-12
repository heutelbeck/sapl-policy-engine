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
package io.sapl.node;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import lombok.val;

/**
 * Renders a small landing card at the root path: a branded HTML page for
 * browser visitors, a tiny JSON descriptor for API clients (content
 * negotiation via the {@code Accept} header). Shows the engine version,
 * git commit and live health status.
 * <p>
 * Version is read from the running jar's {@code MANIFEST.MF}
 * Implementation-Version (populated by
 * {@code spring-boot-maven-plugin:repackage}); commit comes from the
 * {@code git.properties} resource (shipped by {@code sapl-pdp} via the
 * git-commit-id plugin and auto-bound to {@code git.commit.id.abbrev}).
 */
@Profile("!cli")
@RestController
class RootIndexController {

    private static final String STATUS_UNKNOWN = "UNKNOWN";

    private final ObjectProvider<HealthEndpoint> healthEndpoint;
    private final String                         commit;
    private final String                         version;

    RootIndexController(ObjectProvider<HealthEndpoint> healthEndpoint,
            @Value("${git.commit.id.abbrev:}") String commit) {
        this.healthEndpoint = healthEndpoint;
        this.commit         = commit;
        val pkgVersion = SaplNodeApplication.class.getPackage().getImplementationVersion();
        this.version = pkgVersion == null ? "" : pkgVersion;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    ModelAndView indexHtml() {
        val status = healthStatus();
        val up     = "UP".equals(status);
        val model  = new LinkedHashMap<String, Object>();
        model.put("version", version);
        model.put("commit", commit);
        model.put("health", status);
        model.put("healthUp", up);
        model.put("healthDown", !up);
        return new ModelAndView("index", model);
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> indexJson() {
        return Map.of("name", "SAPL Node", "description", "Streaming policy decision point.", "version", version,
                "commit", commit, "health", healthStatus(), "links",
                Map.of("docs", "/scalar", "openapi", "/v3/api-docs", "health", "/actuator/health", "info",
                        "/actuator/info", "website", "https://sapl.io"));
    }

    private String healthStatus() {
        val endpoint = healthEndpoint.getIfAvailable();
        if (endpoint == null) {
            return STATUS_UNKNOWN;
        }
        val health = endpoint.health();
        val status = health == null ? null : health.getStatus();
        return status == null ? STATUS_UNKNOWN : status.getCode();
    }

}
