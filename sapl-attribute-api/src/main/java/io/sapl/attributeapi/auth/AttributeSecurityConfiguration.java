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
package io.sapl.attributeapi.auth;

import io.sapl.attributeapi.auth.apikey.ApiKeyAuthenticationFilter;
import io.sapl.attributeapi.auth.apikey.ApiKeyAuthenticationProvider;
import io.sapl.attributeapi.auth.apikey.ApiKeyAuthenticationService;
import io.sapl.attributeapi.auth.oauth2.PdpIdJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Provides the security configuration/filter for the Attribute API server. Effective when
 * the server is running standalone and replaced when it's running embedded.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AttributeApiSecurityProperties.class)
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
@ConditionalOnMissingClass("io.sapl.node.SaplNodeApplication")
@RequiredArgsConstructor
public class AttributeSecurityConfiguration {
    private static final String ERROR_NO_AUTH_METHOD_SET  = "No authentication method is set.";
    private static final String WARN_NO_AUTH_CONFIGURED   = "Server has been configured to reply to requests without authentication.";
    private static final String INFO_BASIC_AUTH_ACTIVATED = "Basic authentication activated.";
    private static final String WARN_BASIC_NO_USERS       = "Basic authentication is enabled but no users with basic credentials are configured.";
    private static final String INFO_API_KEY_ACTIVATED    = "API key authentication activated.";
    private static final String WARN_API_KEY_NO_USERS     = "API key authentication is enabled but no api key is defined.";
    private static final String INFO_OAUTH2_ACTIVATED     = "OAuth2 authentication activated.";
    private static final String ERROR_MISSING_ISSUER_URI  = "OAuth2 authentication is enabled but 'spring.security.oauth2.resourceserver.jwt.issuer-uri' is not set.";
    private static final String WARN_BASIC_AUTH_CSRF      = "Basic authentication is enabled. Browsers auto-attach Basic credentials, which exposes a CSRF surface that API key and OAuth2 JWT do not. Prefer Bearer auth for production. See https://sapl.io/docs/latest/7_6_Security.";

    private final AttributeApiSecurityProperties properties;
    private final PasswordEncoder                encoder;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    /**
     * Builds the security chain for /api/attributes/* and leaves other chains
     * unaffected. So, other chains can handler their part themselves.
     *
     * @param http the security builder from the Spring framework.
     * @return the security filter chain of the Attribute API
     * @throws IllegalStateException if no authentication method is enabled
     * @throws Exception if Spring Security fails to build the chain
     */
    @Bean
    @Order(1)
    SecurityFilterChain attributeApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/attributes/**");
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (noAuthenticationMechanismIsDefined()) {
            throw new IllegalStateException(ERROR_NO_AUTH_METHOD_SET);
        }

        if (properties.isAllowNoAuth() && !properties.isAllowBasicAuth() && !properties.isAllowApiKeyAuth()
                && !properties.isAllowOAuth2Auth()) {
            log.warn(WARN_NO_AUTH_CONFIGURED);
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        if (properties.isAllowBasicAuth()) {
            httpBasicAllowed(http);
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }

        if (properties.isAllowApiKeyAuth()) {
            apiKeyAllowed(http);
        }

        if (properties.isAllowOAuth2Auth()) {
            oauth2Allowed(http);
        }

        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

        return http.build();
    }

    private void httpBasicAllowed(HttpSecurity http) {
        log.info(INFO_BASIC_AUTH_ACTIVATED);
        log.warn(WARN_BASIC_AUTH_CSRF);
        if (!hasBasicAuthUsers()) {
            log.warn(WARN_BASIC_NO_USERS);
        }
        http.httpBasic(withDefaults());
    }

    private void apiKeyAllowed(HttpSecurity http) {
        var authenticationEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint));

        log.info(INFO_API_KEY_ACTIVATED);

        if (!hasApiKeyUsers()) {
            log.warn(WARN_API_KEY_NO_USERS);
        }

        AuthenticationManager apiKeyAuthenticationManager = new ProviderManager(
                new ApiKeyAuthenticationProvider(new ApiKeyAuthenticationService(properties, encoder)));
        http.addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyAuthenticationManager, authenticationEntryPoint),
                UsernamePasswordAuthenticationFilter.class);
    }

    private void oauth2Allowed(HttpSecurity http) {
        log.info(INFO_OAUTH2_ACTIVATED);

        var converter = new PdpIdJwtAuthenticationConverter(properties.getOauth2().getOidcPdpIdClaim());
        var decoder   = jwtDecoder();

        // Skip sapl_ API keys for the JWT decoder because it's used by the api. OAuth2
        // ignores it.
        http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(request -> {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith(ApiKeyAuthenticationFilter.API_KEY_PREFIX)) {
                return null;
            }
            return new DefaultBearerTokenResolver().resolve(request);
        }).jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
    }

    /**
     * Looks up the Basic auth users from {@code io.sapl.attribute-api.users}.
     *
     * @return the user found in the lookup for basic auth.
     */
    @Bean
    UserDetailsService userDetailsService() {
        return username -> properties.getUsers().stream().filter(user -> username.equals(user.getUsername()))
                .findFirst()
                .map(user -> new AttributeApiUserDetails(user.getUsername(), user.getSecret(), user.getPdpId()))
                .orElseThrow(() -> new UsernameNotFoundException("User '" + username + "' not found"));
    }

    // static, so that spring can inject the encoder in the constructor if not
    // already available.
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    static PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    // boot check: error if no auth check is defined. abort application start.
    private boolean noAuthenticationMechanismIsDefined() {
        return !properties.isAllowNoAuth() && !properties.isAllowBasicAuth() && !properties.isAllowApiKeyAuth()
                && !properties.isAllowOAuth2Auth();
    }

    // boot check: are there any users for this auth mode enabled?
    private boolean hasBasicAuthUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getUsername() != null);
    }

    // boot check: are there any users for this auth mode enabled?
    private boolean hasApiKeyUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getApiKeyId() != null);
    }

    private JwtDecoder jwtDecoder() {
        if (jwtIssuerUri.isBlank()) {
            throw new IllegalStateException(ERROR_MISSING_ISSUER_URI);
        }
        return JwtDecoders.fromIssuerLocation(jwtIssuerUri);
    }

    /**
     * Resolves the pdp id of the authenticated user. Returns an empty string if
     * no-auth mode is enabled and the service will use the default pdp id. If
     * embedded this service will not be executed because it's included in the main
     * application.
     *
     * @return the resolver for the pdp id of the current request.
     */
    @Bean
    @ConditionalOnMissingBean(BlockingTenantResolver.class)
    BlockingTenantResolver attributeApiBlockingTenantResolver() {
        return () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !(authentication.getPrincipal() instanceof AttributeApiUserDetails principal)) {
                return "";
            }
            var pdpId = principal.getPdpId();
            return pdpId != null ? pdpId : "";
        };
    }
}
