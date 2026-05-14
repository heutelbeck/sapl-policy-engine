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
package io.sapl.spring.pdp.remote;

import javax.net.ssl.SSLException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.pdp.remote.DelegatingBlockingPolicyDecisionPoint;
import io.sapl.pdp.remote.ProtobufRemoteReactivePolicyDecisionPoint;
import io.sapl.pdp.remote.RemoteHttpReactivePolicyDecisionPoint.RemoteHttpPolicyDecisionPointBuilder;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "io.sapl.pdp.remote", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@EnableConfigurationProperties(RemotePDPProperties.class)
public class RemotePDPAutoConfiguration {

    private static final String ERROR_UNSUPPORTED_REMOTE_PDP_CONNECTION_TYPE = "Unsupported remote PDP connection type: %s";

    private static final String TYPE_HTTP    = "http";
    private static final String TYPE_RSOCKET = "rsocket";

    private final RemotePDPProperties configuration;

    @Bean
    @ConditionalOnMissingBean
    ReactivePolicyDecisionPoint policyDecisionPoint() throws SSLException {
        return switch (configuration.getType()) {
        case TYPE_HTTP    -> buildHttpPdp();
        case TYPE_RSOCKET -> buildRSocketPdp();
        default           -> throw new IllegalStateException(
                ERROR_UNSUPPORTED_REMOTE_PDP_CONNECTION_TYPE.formatted(configuration.getType()));
        };
    }

    @Bean
    @ConditionalOnMissingBean
    StreamingPolicyDecisionPoint blockingPolicyDecisionPoint(ReactivePolicyDecisionPoint reactivePdp) {
        return new DelegatingBlockingPolicyDecisionPoint(reactivePdp);
    }

    private ReactivePolicyDecisionPoint buildHttpPdp() throws SSLException {
        log.info("Binding to http remote PDP server: {}", configuration.getHost());
        val builder = RemotePolicyDecisionPoint.builder().http().baseUrl(configuration.getHost());
        applyHttpAuthentication(builder);
        if (configuration.isIgnoreCertificates()) {
            builder.withUnsecureSSL();
        }
        return builder.build();
    }

    private void applyHttpAuthentication(RemoteHttpPolicyDecisionPointBuilder builder) {
        if (configuration.isTokenRelay()) {
            log.info("Connecting with token relay (forwarding user credential per request)");
            builder.tokenRelay(RemotePDPAutoConfiguration::extractCurrentToken);
        } else if (!configuration.getKey().isEmpty()) {
            log.info("Connecting with basic authentication");
            builder.basicAuth(configuration.getKey(), configuration.getSecret());
        } else if (!configuration.getApiKey().isEmpty()) {
            log.info("Connecting with apiKey authentication");
            builder.apiKey(configuration.getApiKey());
        }
    }

    private ReactivePolicyDecisionPoint buildRSocketPdp() throws SSLException {
        val builder = RemotePolicyDecisionPoint.builder().rsocket();
        if (configuration.getSocketPath().isEmpty()) {
            log.info("Binding to rsocket remote PDP server: {}:{}", configuration.getHost(), configuration.getPort());
            builder.host(configuration.getHost()).port(configuration.getPort());
        } else {
            log.info("Binding to rsocket remote PDP server via unix domain socket: {}", configuration.getSocketPath());
            builder.socketPath(configuration.getSocketPath());
        }
        builder.keepAlive(configuration.getKeepAlive(), configuration.getMaxLifeTime());
        applyRSocketAuthentication(builder);
        applyRSocketTls(builder);
        return builder.build();
    }

    private void applyRSocketAuthentication(ProtobufRemoteReactivePolicyDecisionPoint.Builder builder) {
        if (!configuration.getKey().isEmpty()) {
            log.info("Connecting with basic authentication");
            builder.basicAuth(configuration.getKey(), configuration.getSecret());
        } else if (!configuration.getApiKey().isEmpty()) {
            log.info("Connecting with apiKey authentication");
            builder.apiKey(configuration.getApiKey());
        }
    }

    private void applyRSocketTls(ProtobufRemoteReactivePolicyDecisionPoint.Builder builder) throws SSLException {
        if (configuration.isIgnoreCertificates()) {
            builder.withUnsecureSSL();
        } else if (configuration.isTls()) {
            builder.secure();
        }
    }

    private static String extractCurrentToken() {
        val auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String token) {
            return token;
        }
        return null;
    }

}
