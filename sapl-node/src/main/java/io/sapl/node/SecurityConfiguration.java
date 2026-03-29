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

import static org.springframework.security.config.Customizer.withDefaults;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.HttpBasicSpec;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.node.apikey.ApiKeyReactiveAuthenticationManager;
import io.sapl.node.apikey.ApiKeyService;
import io.sapl.api.pdp.MultiTenantPolicyDecisionPoint;
import io.sapl.spring.config.PdpIdAuthenticationExtractor;
import io.sapl.node.auth.SaplAuthenticationToken;
import io.sapl.node.auth.SaplJwtAuthenticationConverter;
import io.sapl.node.auth.SaplJwtAuthenticationToken;
import io.sapl.node.auth.SaplUser;
import io.sapl.node.auth.SaplReactiveUserDetailsService;
import io.sapl.node.auth.UserLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Security configuration for SAPL Node with unified user management.
 */
@Slf4j
@Configuration
@Profile("!cli")
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final String JWT_ISSUER_URI_PROPERTY         = "spring.security.oauth2.resourceserver.jwt.issuer-uri";
    private static final String ERROR_JWT_ISSUER_REQUIRED       = "If JWT authentication is active, a token issuer must be supplied. Please set: '"
            + JWT_ISSUER_URI_PROPERTY + "'.";
    private static final String ERROR_NO_AUTH_MECHANISM_DEFINED = "No authentication mechanism for clients defined. Set up your local/container configuration. If the server should respond to unauthenticated requests, this has to be explicitly activated.";
    private static final String ERROR_NO_BASIC_AUTH_USERS       = "Basic authentication is enabled but no users with Basic credentials are configured. Please add users under 'io.sapl.node.users' with 'basic' credentials.";
    private static final String WARN_NO_API_KEYS                = "API key authentication is enabled but no users with API keys are configured. Please add users under 'io.sapl.node.users' with 'apiKey' credentials.";

    private final ApiKeyService      apiKeyService;
    private final SaplNodeProperties pdpProperties;
    private final UserLookupService  userLookupService;

    @Value("${" + JWT_ISSUER_URI_PROPERTY + ":#{null}}")
    private String jwtIssuerURI;

    @Bean
    SaplReactiveUserDetailsService saplUserDetailsService() {
        return new SaplReactiveUserDetailsService(userLookupService);
    }

    @Bean
    PdpIdAuthenticationExtractor pdpIdAuthenticationExtractor() {
        val userDetailsService = saplUserDetailsService();
        return authentication -> {
            if (authentication instanceof SaplAuthenticationToken saplAuth) {
                return Mono.just(saplAuth.getPdpId());
            }
            if (authentication instanceof SaplJwtAuthenticationToken jwtAuth) {
                return Mono.just(jwtAuth.getPdpId());
            }
            val principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                return userDetailsService.resolveSaplUser(userDetails.getUsername()).map(SaplUser::pdpId);
            }
            return Mono.just(MultiTenantPolicyDecisionPoint.DEFAULT_PDP_ID);
        };
    }

    /**
     * Provides a no-op password service to satisfy Spring Security requirements.
     * Password updates are not supported; users are configured in application.yml.
     *
     * @return the reactive user details password service
     */
    @Bean
    ReactiveUserDetailsPasswordService reactiveUserDetailsPasswordService() {
        return (user, newPassword) -> Mono.just(user);
    }

    @Bean
    SecurityWebFilterChain securityFilterChainLocal(ServerHttpSecurity http) {
        http = http.csrf(CsrfSpec::disable);

        if (noAuthenticationMechanismIsDefined()) {
            throw new IllegalStateException(ERROR_NO_AUTH_MECHANISM_DEFINED);
        }

        if (pdpProperties.isAllowNoAuth() && !pdpProperties.isAllowBasicAuth() && !pdpProperties.isAllowApiKeyAuth()
                && !pdpProperties.isAllowOauth2Auth()) {
            // No authentication at all: minimize security filter chain overhead
            log.warn("Server has been configured to reply to requests without authentication.");
            return http.csrf(CsrfSpec::disable).formLogin(FormLoginSpec::disable).httpBasic(HttpBasicSpec::disable)
                    .requestCache(spec -> spec.disable())
                    .authorizeExchange(exchange -> exchange.anyExchange().permitAll()).build();
        }

        if (pdpProperties.isAllowNoAuth()) {
            log.warn("Server has been configured to reply to requests without authentication.");
            http = http.authorizeExchange(exchange -> exchange.pathMatchers("/**").permitAll());
        } else {
            http = http.authorizeExchange(exchange -> exchange.pathMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll().anyExchange().authenticated());
        }

        if (pdpProperties.isAllowApiKeyAuth()) {
            log.info("API key authentication activated.");
            if (!hasApiKeyUsers()) {
                log.warn(WARN_NO_API_KEYS);
            }
            val customAuthenticationWebFilter = new AuthenticationWebFilter(new ApiKeyReactiveAuthenticationManager());
            customAuthenticationWebFilter
                    .setServerAuthenticationConverter(apiKeyService.getHttpApiKeyAuthenticationConverter());
            http = http.addFilterAt(customAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        }

        if (pdpProperties.isAllowBasicAuth()) {
            log.info("Basic authentication activated.");
            if (!hasBasicAuthUsers()) {
                throw new IllegalStateException(ERROR_NO_BASIC_AUTH_USERS);
            }
            http = http.httpBasic(withDefaults());
        }

        if (pdpProperties.isAllowOauth2Auth()) {
            log.info("OAuth2 authentication activated. Accepting JWT tokens from issuer: {}", jwtIssuerURI);
            if (jwtIssuerURI == null) {
                throw new IllegalStateException(ERROR_JWT_ISSUER_REQUIRED);
            }
            http = http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenConverter(createBearerTokenConverter())
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(new SaplJwtAuthenticationConverter(pdpProperties))));
        }

        return http.formLogin(FormLoginSpec::disable).build();
    }

    private ServerBearerTokenAuthenticationConverter createBearerTokenConverter() {
        return new ServerBearerTokenAuthenticationConverter() {
            @Override
            public @NonNull Mono<Authentication> convert(@NonNull ServerWebExchange exchange) {
                if (ApiKeyService.getApiKeyToken(exchange).isPresent()) {
                    return Mono.empty();
                }
                return super.convert(exchange);
            }
        };
    }

    private boolean noAuthenticationMechanismIsDefined() {
        return !pdpProperties.isAllowNoAuth() && !pdpProperties.isAllowBasicAuth() && !pdpProperties.isAllowApiKeyAuth()
                && !pdpProperties.isAllowOauth2Auth();
    }

    private boolean hasBasicAuthUsers() {
        return pdpProperties.getUsers().stream().anyMatch(user -> user.getBasic() != null);
    }

    private boolean hasApiKeyUsers() {
        return pdpProperties.getUsers().stream().anyMatch(user -> user.getApiKey() != null);
    }

    /**
     * Decodes JSON Web Token (JWT) according to the configuration that was
     * initialized by the OpenID Provider specified in the jwtIssuerURI.
     *
     * @return the reactive JWT decoder
     */
    @Bean
    ReactiveJwtDecoder jwtDecoder() {
        if (pdpProperties.isAllowOauth2Auth()) {
            return ReactiveJwtDecoders.fromIssuerLocation(jwtIssuerURI);
        }
        return null;
    }

}
