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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.config.http.SessionCreationPolicy;
import static org.springframework.security.config.Customizer.withDefaults;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AttributeApiSecurityProperties.class)
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
@RequiredArgsConstructor

public class AttributeSecurityConfiguration {
    private static final String BEARER_PREFIX             = "Bearer ";
    private static final String ERROR_NO_AUTH_METHOD_SET  = "No authentication method is set";
    private static final String WARN_NO_AUTH_CONFIGURED   = "Server has been configured to reply to requests without authentication.";
    private static final String INFO_BASIC_AUTH_ACTIVATED = "Basic authentication activated.";
    private static final String WARN_BASIC_NO_USERS       = "Basic authentication is enabled but no users with basic credentials are configured.";
    private static final String INFO_API_KEY_ACTIVATED    = "API key authentication activated.";
    private static final String WARN_API_KEY_NO_USERS     = "API key authentication is enabled but no api key is defined.";
    private static final String INFO_OAUTH2_ACTIVATED     = "OAuth2 authentication activated";
    private static final String ERROR_MISSING_ISSUER_URI  = "OAuth2 authentication is enabled but 'spring.security.oauth2.resourceserver.jwt.issuer-uri' is not set.";

    private final AttributeApiSecurityProperties properties;
    private final PasswordEncoder                encoder;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    @Bean
    @Order(1)
    SecurityFilterChain attributeApiSecurityFilterChain(HttpSecurity http) throws Exception {
        // Scoped to this module's endpoints only, so it can coexist with a
        // host application's own catch-all SecurityFilterChain (e.g. when
        // embedded inside sapl-node).
        http.securityMatcher("/api/attributes/**");

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()).ignoringRequestMatchers(request -> {
                    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                    return authHeader == null || authHeader.startsWith(BEARER_PREFIX);
                }));

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

        // API keys are also carried as "Authorization: Bearer sapl_..." - leave
        // those alone here so the apiKeyFilter above gets a chance to handle
        // them instead of failing JWT decoding.
        http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(request -> {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith(ApiKeyAuthenticationFilter.API_KEY_PREFIX)) {
                return null;
            }
            return new DefaultBearerTokenResolver().resolve(request);
        }).jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new AttributeApiUserDetailsService(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public static PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    private boolean noAuthenticationMechanismIsDefined() {
        return !properties.isAllowNoAuth() && !properties.isAllowBasicAuth() && !properties.isAllowApiKeyAuth()
                && !properties.isAllowOAuth2Auth();
    }

    private boolean hasBasicAuthUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getUsername() != null);
    }

    private boolean hasApiKeyUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getApiKeyId() != null);
    }

    private JwtDecoder jwtDecoder() {
        if (jwtIssuerUri == null || jwtIssuerUri.isBlank()) {
            throw new IllegalStateException(ERROR_MISSING_ISSUER_URI);
        }
        return JwtDecoders.fromIssuerLocation(jwtIssuerUri);
    }
}
