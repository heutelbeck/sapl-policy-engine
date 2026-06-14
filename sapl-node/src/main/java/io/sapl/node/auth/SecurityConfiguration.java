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
package io.sapl.node.auth;

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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.boot.SaplStartupConfigurationException;
import io.sapl.node.auth.apikey.ApiKeyAuthenticationFilter;
import io.sapl.node.auth.apikey.ApiKeyAuthenticationManager;
import io.sapl.node.auth.apikey.ApiKeyService;
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

import java.util.Collections;
import java.util.List;

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

    private static final String JWT_ISSUER_URI_PROPERTY = "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    private static final String ERROR_NO_AUTH_MECHANISM  = "SAPL Node refused to start. No authentication mechanism is configured.";
    private static final String ACTION_NO_AUTH_MECHANISM = """
            This fail closed default protects production from accepting
            unauthenticated traffic by accident.

            Quickest unblock for local development:

              sapl server --no-auth

            Or enable a real auth method, in application.yml or as --property=value:

              io.sapl.node.allow-basic-auth=true     plus users with basic credentials
              io.sapl.node.allow-api-key-auth=true   plus users with api keys
              io.sapl.node.allow-oauth2-auth=true    plus spring.security.oauth2.resourceserver.jwt.issuer-uri

            See https://sapl.io/docs/latest/7_2_Configuration for details.""";

    private static final String ERROR_NO_BASIC_AUTH_USERS  = "SAPL Node refused to start. Basic authentication is enabled but no users with basic credentials are configured.";
    private static final String ACTION_NO_BASIC_AUTH_USERS = """
            Add at least one user under io.sapl.node.users with a basic block,
            for example:

              io:
                sapl:
                  node:
                    users:
                      - id: alice
                        pdp-id: default
                        basic:
                          username: alice
                          secret: "{argon2id}<encoded password>"

            Generate a credential with:

              sapl generate basic --id alice""";

    private static final String ERROR_JWT_ISSUER_REQUIRED  = "SAPL Node refused to start. OAuth2 is enabled but no JWT issuer URI is configured.";
    private static final String ACTION_JWT_ISSUER_REQUIRED = """
            Configure the JWT issuer URI for your identity provider:

              spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.org/realms/sapl

            The issuer URI is the value the IdP advertises as iss in the
            tokens it signs. SAPL Node fetches the JWKS from this issuer on
            the first decision call, then caches it.""";

    private static final String WARN_BASIC_AUTH_CSRF = "Basic authentication is enabled. Browsers auto-attach Basic credentials, which exposes a CSRF surface that API key and OAuth2 JWT do not. Prefer Bearer auth for production. See https://sapl.io/docs/latest/7_6_Security.";

    private static final String WARN_NO_API_KEYS = "API key authentication is enabled but no users with API keys are configured. Add users under io.sapl.node.users with an apiKey field.";

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
     * {@link io.sapl.node.auth.http.HttpAuthHandler} inside the raw servlets.
     *
     * @return the customizer
     */
    @Bean
    WebSecurityCustomizer pdpEndpointSecurityBypass() {
        return web -> web.ignoring().requestMatchers("/api/pdp/**");
    }

    @Bean
    HttpFirewall pdpBypassHttpFirewall() {
        val strict = new StrictHttpFirewall();
        val pass   = new DefaultHttpFirewall();
        return new HttpFirewall() {
            @Override
            public FirewalledRequest getFirewalledRequest(HttpServletRequest request) {
                if (request.getRequestURI().startsWith("/api/pdp/")) {
                    return pass.getFirewalledRequest(request);
                }
                return strict.getFirewalledRequest(request);
            }

            @Override
            public HttpServletResponse getFirewalledResponse(HttpServletResponse response) {
                return strict.getFirewalledResponse(response);
            }
        };
    }

    @Bean
    RequestRejectedHandler pdpBypassRequestRejectedHandler() {
        return (request, response, requestRejectedException) -> response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Bad request");
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
        // CSRF disabled. Stateless API, Bearer auth headers are not
        // browser-auto-attached. Basic is the exception and emits a WARN.
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable);

        if (noAuthenticationMechanismIsDefined()) {
            throw new SaplStartupConfigurationException(ERROR_NO_AUTH_MECHANISM, ACTION_NO_AUTH_MECHANISM);
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
            http.authorizeHttpRequests(
                    requests -> requests
                            .requestMatchers("/", "/actuator/health", "/actuator/health/**", "/error", "/error/**",
                                    "/css/**", "/favicon.png", "/sapl-icon.svg", "/sapl-icon-light.svg", "/scalar",
                                    "/scalar/**", "/v3/api-docs", "/v3/api-docs/**")
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
            log.warn(WARN_BASIC_AUTH_CSRF);
            if (!hasBasicAuthUsers()) {
                throw new SaplStartupConfigurationException(ERROR_NO_BASIC_AUTH_USERS, ACTION_NO_BASIC_AUTH_USERS);
            }
            http.httpBasic(withDefaults());
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }

        if (pdpProperties.isAllowOauth2Auth()) {
            log.info("OAuth2 authentication activated. Accepting JWT tokens from issuer: {}", jwtIssuerURI);
            if (jwtIssuerURI == null) {
                throw new SaplStartupConfigurationException(ERROR_JWT_ISSUER_REQUIRED, ACTION_JWT_ISSUER_REQUIRED);
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
        if (!pdpProperties.isAllowOauth2Auth()) {
            return null;
        }
        return new SupplierJwtDecoder(() -> {
            try {
                val decoder   = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(jwtIssuerURI);
                val audiences = pdpProperties.getOauth().getAudiences();
                if (!audiences.isEmpty()) {
                    decoder.setJwtValidator(audienceValidator(jwtIssuerURI, audiences));
                }
                return decoder;
            } catch (Exception e) {
                log.warn("OIDC discovery against issuer {} failed: {}; OAuth2 token validation will retry on the next "
                        + "request", jwtIssuerURI, e.getMessage());
                throw new JwtException("OIDC issuer unavailable: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Builds the token validator for the configured issuer. When the audience
     * allowlist is non-empty the default issuer and timestamp validation is
     * combined with an audience check, so a token minted for a different
     * resource server on the same issuer is rejected. An empty allowlist
     * disables the audience check and preserves the default validation.
     *
     * @param issuer the expected token issuer
     * @param audiences the accepted audience values, or empty to disable the check
     * @return the combined token validator
     */
    static OAuth2TokenValidator<Jwt> audienceValidator(String issuer, List<String> audiences) {
        val defaultValidator = JwtValidators.createDefaultWithIssuer(issuer);
        if (audiences.isEmpty()) {
            return defaultValidator;
        }
        val audienceCheck = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && !Collections.disjoint(aud, audiences));
        return new DelegatingOAuth2TokenValidator<>(defaultValidator, audienceCheck);
    }
}
