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
package io.sapl.benchmark;

import static io.sapl.benchmark.BenchmarkConfiguration.DOCKER_DEFAULT_HTTP_PORT;
import static io.sapl.benchmark.BenchmarkConfiguration.DOCKER_DEFAULT_RSOCKET_PORT;

import java.util.List;

import org.testcontainers.containers.GenericContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;

/**
 * This class is holding the benchmark execution context derived from
 * configuration and optional the container environment. The class object is
 * then mapped to json, so that they can be passed via command line parameters
 * to the JMH process executing the actual benchmark.
 */
@Getter
@Setter
@RequiredArgsConstructor
public class BenchmarkExecutionContext {

    private String                    rsocketHost;
    private Integer                   rsocketPort;
    private String                    basicClientKey;
    private String                    basicClientSecret;
    private String                    apiKey;
    private String                    oauth2ClientSecret;
    private String                    oauth2Scope;
    private String                    oauth2TokenUri;
    private String                    httpBaseUrl;
    private boolean                   useNoAuth;
    private boolean                   useBasicAuth;
    private boolean                   useAuthApiKey;
    private boolean                   useOauth2;
    private String                    oauth2ClientId;
    private boolean                   useSsl;
    private List<Integer>             threadList;
    private AuthorizationSubscription authorizationSubscription;
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new SaplJacksonModule());

    @SneakyThrows
    public static BenchmarkExecutionContext fromString(String jsonString) {
        return MAPPER.readValue(jsonString, BenchmarkExecutionContext.class);
    }

    @SneakyThrows
    public String toJsonString() {
        return MAPPER.writeValueAsString(this);
    }

    public static BenchmarkExecutionContext fromBenchmarkConfiguration(BenchmarkConfiguration cfg,
            GenericContainer<?> pdpContainer, GenericContainer<?> oauthContainer) {

        // initialize new context
        final var context = new BenchmarkExecutionContext();

        // get docker/remote specific settings
        context.authorizationSubscription = cfg.getAuthorizationSubscription();
        if (cfg.requiredDockerEnvironment()) {
            context.rsocketHost = pdpContainer.getHost();
            context.rsocketPort = pdpContainer.getMappedPort(DOCKER_DEFAULT_RSOCKET_PORT);
            context.useSsl      = cfg.isDockerUseSsl();
            if (context.useSsl) {
                context.httpBaseUrl = "https://" + pdpContainer.getHost() + ":"
                        + pdpContainer.getMappedPort(DOCKER_DEFAULT_HTTP_PORT);
            } else {
                // noinspection HttpUrlsUsage
                context.httpBaseUrl = "http://" + pdpContainer.getHost() + ":"
                        + pdpContainer.getMappedPort(DOCKER_DEFAULT_HTTP_PORT);
            }
        } else {
            context.rsocketHost = cfg.getRemoteRsocketHost();
            context.rsocketPort = cfg.getRemoteRsocketPort();
            context.useSsl      = cfg.isRemoteUseSsl();
            context.httpBaseUrl = cfg.getRemoteBaseUrl();
        }

        // collecting auth specific settings
        context.basicClientKey    = cfg.getBasicClientKey();
        context.basicClientSecret = cfg.getBasicClientSecret();
        context.apiKey            = cfg.getApiKeySecret();

        // collecting OAuth2 specific settings
        if (cfg.isUseOauth2() && cfg.isOauth2MockServer()) {
            context.oauth2TokenUri = "http://auth-host:" + oauthContainer.getMappedPort(8080) + "/default/token";
        } else {
            context.oauth2TokenUri = cfg.getOauth2TokenUri();
        }
        context.oauth2ClientId     = cfg.getOauth2ClientId();
        context.oauth2ClientSecret = cfg.getOauth2ClientSecret();
        context.oauth2Scope        = cfg.getOauth2Scope();

        // specifying which auth methods to use
        context.useNoAuth     = cfg.isUseNoAuth();
        context.useBasicAuth  = cfg.isUseBasicAuth();
        context.useAuthApiKey = cfg.isUseAuthApiKey();
        context.useOauth2     = cfg.isUseOauth2();

        context.threadList = cfg.getThreadList();
        return context;
    }

    public static BenchmarkExecutionContext fromBenchmarkConfiguration(BenchmarkConfiguration cfg) {
        return BenchmarkExecutionContext.fromBenchmarkConfiguration(cfg, null, null);
    }
}
