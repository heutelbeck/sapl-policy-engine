/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RemotePDPProperties.class)
public class RemotePDPAutoConfiguration {

    private final RemotePDPProperties configuration;

    @Bean
    @ConditionalOnMissingBean
    PolicyDecisionPoint policyDecisionPoint() throws SSLException {
        if ("rsocket".equals(configuration.getType())) {
            log.info("Binding to rsocket remote PDP server: {}:{}", configuration.getRsocketHost(),
                    configuration.getRsocketPort());
            var builder = RemotePolicyDecisionPoint.builder().rsocket().host(configuration.getRsocketHost())
                    .port(configuration.getRsocketPort());
            if (!configuration.getKey().isEmpty()) {
                log.info("Connecting with basic authentication");
                builder.basicAuth(configuration.getKey(), configuration.getSecret());
            } else if (!configuration.getApiKey().isEmpty()) {
                log.info("Connecting with apiKey authentication");
                builder.apiKey(configuration.getApiKey());
            }
            if (configuration.isIgnoreCertificates()) {
                builder.withUnsecureSSL();
            }
            return builder.build();

        } else {
            log.info("Binding to http remote PDP server: {}", configuration.getHost());
            var builder = RemotePolicyDecisionPoint.builder().http().baseUrl(configuration.getHost());
            if (!configuration.getKey().isEmpty()) {
                log.info("Connecting with basic authentication");
                builder.basicAuth(configuration.getKey(), configuration.getSecret());
            } else if (!configuration.getApiKey().isEmpty()) {
                log.info("Connecting with apiKey authentication");
                builder.apiKey(configuration.getApiKey());
            }
            if (configuration.isIgnoreCertificates()) {
                builder.withUnsecureSSL();
            }
            return builder.build();
        }
    }

}
