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

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import io.sapl.node.apikey.ApiKeyAuthenticationFilter;
import io.sapl.node.apikey.ApiKeyAuthenticationManager;
import io.sapl.node.apikey.ApiKeyService;
import io.sapl.node.auth.SaplAuthenticationToken;
import io.sapl.node.auth.DefaultBlockingTenantResolver;
import io.sapl.node.auth.PdpIdAuthenticationExtractorBlocking;
import io.sapl.node.auth.SaplJwtAuthenticationConverter;
import io.sapl.node.auth.SaplJwtAuthenticationToken;
import io.sapl.node.auth.SaplUser;
import io.sapl.node.auth.SaplUserDetailsService;
import io.sapl.node.auth.UserLookupService;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Servlet-based security configuration for SAPL Node with unified user
 * management.
 */
@Slf4j
@Configuration
@Profile("!cli")
@EnableWebSecurity
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
    SaplUserDetailsService saplUserDetailsService() {
        return new SaplUserDetailsService(userLookupService);
    }

    @Bean
    BlockingTenantResolver blockingTenantResolver(PdpIdAuthenticationExtractorBlocking extractor) {
        return new DefaultBlockingTenantResolver(extractor);
    }

    /**
     * Excludes the bypass-Spring PDP HTTP endpoint from the Spring Security
     * filter chain. Authentication for those routes is handled by
     * {@link io.sapl.node.http.auth.HttpAuthHandler} inside the raw servlets.
     *
     * @return the customizer
     */
    @Bean
    WebSecurityCustomizer pdpEndpointSecurityBypass() {
        return web -> web.ignoring().requestMatchers("/api/pdp/**");
    }

    @Bean
    PdpIdAuthenticationExtractorBlocking pdpIdAuthenticationExtractor() {
        val userDetailsService = saplUserDetailsService();
        return authentication -> {
            if (authentication == null) {
                return ReactivePolicyDecisionPoint.DEFAULT_PDP_ID;
            }
            if (authentication instanceof SaplAuthenticationToken saplAuth) {
                return saplAuth.getPdpId();
            }
            if (authentication instanceof SaplJwtAuthenticationToken jwtAuth) {
                return jwtAuth.getPdpId();
            }
            val principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                return userDetailsService.resolveSaplUser(userDetails.getUsername()).map(SaplUser::pdpId)
                        .orElse(ReactivePolicyDecisionPoint.DEFAULT_PDP_ID);
            }
            return ReactivePolicyDecisionPoint.DEFAULT_PDP_ID;
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable);

        if (noAuthenticationMechanismIsDefined()) {
            throw new IllegalStateException(ERROR_NO_AUTH_MECHANISM_DEFINED);
        }

        if (pdpProperties.isAllowNoAuth() && !pdpProperties.isAllowBasicAuth() && !pdpProperties.isAllowApiKeyAuth()
                && !pdpProperties.isAllowOauth2Auth()) {
            log.warn("Server has been configured to reply to requests without authentication.");
            return http.httpBasic(AbstractHttpConfigurer::disable).requestCache(cache -> cache.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }

        if (pdpProperties.isAllowNoAuth()) {
            log.warn("Server has been configured to reply to requests without authentication.");
            http.authorizeHttpRequests(requests -> requests.requestMatchers("/**").permitAll());
        } else {
            http.authorizeHttpRequests(requests -> requests
                    .requestMatchers("/", "/actuator/health", "/actuator/health/**", "/error", "/error/**", "/css/**",
                            "/favicon.png", "/sapl-icon.svg", "/sapl-icon-light.svg")
                    .permitAll().anyRequest().authenticated());
        }

        if (pdpProperties.isAllowApiKeyAuth()) {
            log.info("API key authentication activated.");
            if (!hasApiKeyUsers()) {
                log.warn(WARN_NO_API_KEYS);
            }
            val filter = new ApiKeyAuthenticationFilter(apiKeyService.getHttpApiKeyAuthenticationConverter(),
                    new ApiKeyAuthenticationManager());
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        }

        if (pdpProperties.isAllowBasicAuth()) {
            log.info("Basic authentication activated.");
            if (!hasBasicAuthUsers()) {
                throw new IllegalStateException(ERROR_NO_BASIC_AUTH_USERS);
            }
            http.httpBasic(withDefaults());
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }

        if (pdpProperties.isAllowOauth2Auth()) {
            log.info("OAuth2 authentication activated. Accepting JWT tokens from issuer: {}", jwtIssuerURI);
            if (jwtIssuerURI == null) {
                throw new IllegalStateException(ERROR_JWT_ISSUER_REQUIRED);
            }
            val converter = new SaplJwtAuthenticationConverter(pdpProperties);
            http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(skipSaplApiKeyResolver())
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        }

        return http.build();
    }

    /**
     * Bearer token resolver that skips SAPL API keys (Bearer sapl_*), so the
     * JWT path does not try to validate them. SAPL API keys are handled by
     * {@link ApiKeyAuthenticationFilter}, which runs earlier in the chain.
     */
    private static BearerTokenResolver skipSaplApiKeyResolver() {
        val delegate = new DefaultBearerTokenResolver();
        return request -> {
            if (ApiKeyService.getApiKeyToken(request).isPresent()) {
                return null;
            }
            return delegate.resolve(request);
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
     * JWT decoder bean. Returns {@code null} when OAuth2 is not active so that
     * downstream consumers (e.g. RSocket auth) can skip JWT validation.
     *
     * @return the JWT decoder, or {@code null}
     */
    @Bean
    @Nullable
    JwtDecoder jwtDecoder() {
        if (pdpProperties.isAllowOauth2Auth()) {
            return JwtDecoders.fromIssuerLocation(jwtIssuerURI);
        }
        return null;
    }
}
