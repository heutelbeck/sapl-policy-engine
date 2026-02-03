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

import io.sapl.node.apikey.ApiKeyReactiveAuthenticationManager;
import io.sapl.node.apikey.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.security.config.Customizer.withDefaults;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final String ERROR_BASIC_AUTH_KEY_REQUIRED    = "If Basic authentication is active, a client key must be supplied. Please set: 'io.sapl.node.key'.";
    private static final String ERROR_BASIC_AUTH_SECRET_REQUIRED = "If Basic authentication is active, a client secret must be supplied. Please set: 'io.sapl.node.secret'. As an Argon2 encoded secret.";
    private static final String ERROR_JWT_ISSUER_REQUIRED        = "If JWT authentication is active, a token issuer must be supplied. Please set: 'spring.security.oauth2.resourceserver.jwt.issuer-uri'.";
    private static final String ERROR_NO_AUTH_MECHANISM_DEFINED  = "No authentication mechanism for clients defined. Set up your local/container configuration. If the server should respond to unauthenticated requests, this has to be explicitly activated.";

    private final ApiKeyService      apiKeyService;
    private final SaplNodeProperties pdpProperties;
    private final PasswordEncoder    passwordEncoder;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    private String jwtIssuerURI;

    @Bean
    SecurityWebFilterChain securityFilterChainLocal(ServerHttpSecurity http) {
        http = http.csrf(CsrfSpec::disable);

        if (noAuthenticationMechanismIsDefined()) {
            throw new IllegalStateException(ERROR_NO_AUTH_MECHANISM_DEFINED);
        }

        if (pdpProperties.isAllowNoAuth()) {
            log.warn("Server has been configured to reply to requests without authentication.");
            http = http.authorizeExchange(exchange -> exchange.pathMatchers("/**").permitAll());
        } else {
            // any other request requires the user to be authenticated
            http = http.authorizeExchange(exchange -> exchange.anyExchange().authenticated());
        }

        if (pdpProperties.isAllowApiKeyAuth()) {
            log.info("API key authentication activated.");
            if (pdpProperties.getAllowedApiKeys().isEmpty()) {
                log.warn(
                        "No API keys for clients defined. Please set: 'io.sapl.node.allowedApiKeys'. With a list of valid keys.");
            }
            val customAuthenticationWebFilter = new AuthenticationWebFilter(new ApiKeyReactiveAuthenticationManager());
            customAuthenticationWebFilter
                    .setServerAuthenticationConverter(apiKeyService.getHttpApiKeyAuthenticationConverter());
            http = http.addFilterAt(customAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        }

        if (pdpProperties.isAllowBasicAuth()) {
            log.info("Basic authentication activated.");
            http = http.httpBasic(withDefaults());
        }

        if (pdpProperties.isAllowOauth2Auth()) {
            log.info("OAuth2 authentication activated. Accepting JWT tokens from issuer: {}", jwtIssuerURI);
            if (jwtIssuerURI == null) {
                throw new IllegalStateException(ERROR_JWT_ISSUER_REQUIRED);
            }
            http = http.oauth2ResourceServer(
                    oauth2 -> oauth2.bearerTokenConverter(new ServerBearerTokenAuthenticationConverter() {
                        @Override
                        public Mono<Authentication> convert(ServerWebExchange exchange) {
                            if (ApiKeyService.getApiKeyToken(exchange).isPresent()) {
                                // This Bearer token is used for sapl api key authentication
                                return Mono.empty();
                            } else {
                                return super.convert(exchange);
                            }
                        }
                    }).jwt(Customizer.withDefaults()));
        }

        return http.formLogin(FormLoginSpec::disable).build();
    }

    private boolean noAuthenticationMechanismIsDefined() {
        return !pdpProperties.isAllowNoAuth() && !pdpProperties.isAllowBasicAuth() && !pdpProperties.isAllowApiKeyAuth()
                && !pdpProperties.isAllowOauth2Auth();
    }

    @Bean
    MapReactiveUserDetailsService userDetailsServiceLocal() {
        if (!pdpProperties.isAllowBasicAuth()) {
            return null;
        }
        val key = pdpProperties.getKey();
        if (key == null) {
            throw new IllegalStateException(ERROR_BASIC_AUTH_KEY_REQUIRED);
        }
        val secret = pdpProperties.getSecret();
        if (secret == null) {
            throw new IllegalStateException(ERROR_BASIC_AUTH_SECRET_REQUIRED);
        }
        val client = User.builder().username(key).password(secret).roles("PDP_CLIENT").build();
        return new MapReactiveUserDetailsService(client);
    }

    /**
     * Decodes JSON Web Token (JWT) according to the configuration that was
     * initialized by the OpenID Provider specified in the jwtIssuerURI.
     */
    @Bean
    @ConditionalOnProperty(prefix = "io.sapl.node", name = "allowOauth2Auth", havingValue = "true")
    ReactiveJwtDecoder jwtDecoder() {
        if (pdpProperties.isAllowOauth2Auth()) {
            return ReactiveJwtDecoders.fromIssuerLocation(jwtIssuerURI);
        } else {
            return null;
        }
    }

}
