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
package io.sapl.spring.config;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.spring.subscriptions.SubscriptionSecretsInjector;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

/**
 * Auto-configuration for automatic JWT bearer token injection into
 * authorization subscription secrets.
 * <p>
 * When enabled, this configuration extracts the raw encoded JWT from
 * JwtAuthenticationToken instances and merges it into subscription secrets
 * so the JWT PIP can access it securely.
 * <p>
 * Activation requires:
 * <ul>
 * <li>{@code io.sapl.jwt.inject-token=true} in application properties</li>
 * <li>Spring Security OAuth2 Resource Server on the classpath</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SaplJwtProperties.class)
@ConditionalOnProperty(name = "io.sapl.jwt.inject-token", havingValue = "true")
@ConditionalOnClass(name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
public class JwtSecretsAutoConfiguration {

    private static final String WARN_JWT_EXTRACTION_FAILED = "Could not extract JWT from authentication: {}";

    /**
     * Creates a SubscriptionSecretsInjector that extracts the bearer token from
     * JwtAuthenticationToken and adds it to subscription secrets.
     *
     * @param properties the JWT properties
     * @return a SubscriptionSecretsInjector for JWT tokens
     */
    @Bean
    SubscriptionSecretsInjector jwtSubscriptionSecretsInjector(SaplJwtProperties properties) {
        val secretsKey = properties.getSecretsKey();
        log.info("JWT secrets injection enabled with secrets key '{}'", secretsKey);

        return new SubscriptionSecretsInjector() {
            @Override
            public ObjectValue injectSecrets(Authentication authentication) {
                try {
                    val jwtAuthClass = Class.forName(
                            "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken");
                    if (!jwtAuthClass.isInstance(authentication))
                        return Value.EMPTY_OBJECT;

                    val getToken      = jwtAuthClass.getMethod("getToken");
                    val token         = getToken.invoke(authentication);
                    val getTokenValue = token.getClass().getMethod("getTokenValue");
                    val tokenValue    = (String) getTokenValue.invoke(token);

                    return ObjectValue.builder().put(secretsKey, Value.of(tokenValue)).build();
                } catch (Exception e) {
                    log.warn(WARN_JWT_EXTRACTION_FAILED, e.getMessage());
                    return Value.EMPTY_OBJECT;
                }
            }
        };
    }

}
