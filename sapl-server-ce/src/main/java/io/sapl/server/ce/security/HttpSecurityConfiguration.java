/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.HttpStatusAccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.vaadin.flow.spring.security.VaadinWebSecurity;

import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.apikey.ApiKeyHeaderAuthFilterService;
import io.sapl.server.ce.security.apikey.ApiKeyService;
import io.sapl.server.ce.ui.views.login.LoginView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class HttpSecurityConfiguration extends VaadinWebSecurity {
    @Value("${io.sapl.server.allowBasicAuth:#{false}}")
    private boolean allowBasicAuth;

    @Value("${io.sapl.server.allowApiKeyAuth:#{true}}")
    private boolean allowApiKeyAuth;

    @Value("${io.sapl.server.allowOauth2Auth:#{false}}")
    private boolean allowOauth2Auth;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    private String jwtIssuerURI;

    @Value("${io.sapl.server.allowOAuth2Login:#{false}}")
    private boolean allowOAuth2Login;

    private final ApiKeyHeaderAuthFilterService apiKeyAuthenticationFilterService;

    private static final String GROUPS             = "groups";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM        = "roles";

    /**
     * Decodes JSON Web Token (JWT) according to the configuration that was
     * initialized by the OpenID Provider specified in the jwtIssuerURI.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        if (allowOauth2Auth) {
            return JwtDecoders.fromIssuerLocation(jwtIssuerURI);
        } else {
            return null;
        }
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of(new SimpleGrantedAuthority(ClientDetailsService.CLIENT)));
        return converter;
    }

    /**
     * This filter chain is offering Basic Authn for the API.
     *
     * @param http the HttpSecurity.
     * @return configured HttpSecurity
     * @throws Exception if error occurs during HTTP security configuration
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain apiAuthnFilterChain(HttpSecurity http) throws Exception {

        final var forbidden = new HttpStatusEntryPoint(HttpStatus.FORBIDDEN);

        // @formatter:off
		http = http.securityMatcher("/api/**") // API path
    		       .requestCache(RequestCacheConfigurer::disable)
    		       .formLogin(AbstractHttpConfigurer::disable)
    		       .oauth2Login(AbstractHttpConfigurer::disable)
    		       .logout(AbstractHttpConfigurer::disable)
        	       .csrf(AbstractHttpConfigurer::disable)
        		   .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        		   .exceptionHandling(ex -> ex.authenticationEntryPoint(forbidden) // return 403
        		                              .accessDeniedHandler(new HttpStatusAccessDeniedHandler(HttpStatus.FORBIDDEN)))
                   .httpBasic(b -> b.authenticationEntryPoint(forbidden));

		if (allowApiKeyAuth) {
			log.info("configuring ApiKey for Http authentication");
			http = http.addFilterAt(apiKeyAuthenticationFilterService, UsernamePasswordAuthenticationFilter.class);

		}

        // fix sporadic spring-security issue 9175: https://github.com/spring-projects/spring-security/issues/9175#issuecomment-661879599
        http = http.headers(headers -> headers
                .withObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                    @Override
                    public <O extends HeaderWriterFilter> O postProcess(O headerWriterFilter) {
                        headerWriterFilter.setShouldWriteHeadersEagerly(true);
                        return headerWriterFilter;
                    }
                })
        );

		if (allowOauth2Auth) {
			log.info("configuring Oauth2 authentication with jwtIssuerURI: " + jwtIssuerURI);
			http = http.oauth2ResourceServer(
                    oauth2 -> oauth2
                            .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                            .bearerTokenResolver(new BearerTokenResolver() {
                                final BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
                                @Override
                                public String resolve(HttpServletRequest request) {
                                    if (ApiKeyService.getApiKeyToken(request) != null) {
                                        // This Bearer token is used for sapl api key authentication
                                        return null;
                                    } else {
                                        return defaultResolver.resolve(request);
                                    }
                                }
                    }).jwt(Customizer.withDefaults()));
		}

        if (allowBasicAuth){
            http = http.httpBasic(withDefaults()); // offer basic authentication
        }

        // Enable OAuth2 Login with default setting and change the session creation policy to always for a proper login handling
        if (allowOAuth2Login){
            http = http
                    .oauth2Login(withDefaults())
                    .logout(logout -> logout
                            .logoutUrl("/logout")
                            .invalidateHttpSession(true)
                            .deleteCookies("JSESSIONID")
                            .logoutSuccessUrl("/oauth2"))
                    .authorizeHttpRequests(authorize -> authorize.requestMatchers("/unauthenticated", "/oauth2/**", "/login/**", "/VAADIN/push/**").permitAll());
        }

        // all requests to this end point require the CLIENT role
        http = http.authorizeHttpRequests(authz -> authz.anyRequest().hasAnyAuthority(ClientDetailsService.CLIENT));

		// @formatter:on
        return http.build();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        PathPatternRequestMatcher.Builder match = PathPatternRequestMatcher.withDefaults();
        http = http.authorizeHttpRequests(
                authorize -> authorize.requestMatchers(match.matcher("/images/*.png")).permitAll());

        // Xtext services
        http.csrf(csrf -> csrf.ignoringRequestMatchers(match.matcher(("/xtext-service/**"))));

        super.configure(http);

        // Set another LoginPage if OAuth2 is enabled
        if (allowOAuth2Login) {
            setOAuth2LoginPage(http, "/oauth2 ");
        } else {
            setLoginView(http, LoginView.class);
        }
    }

    // Important to extract the OAuth2 roles so that the Role admin is identified
    // correctly
    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapperForKeycloak2() {
        return authorities -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            GrantedAuthority      authority         = authorities.iterator().next();
            boolean               isOidc            = authority instanceof OidcUserAuthority;

            if (isOidc) {
                OidcUserAuthority oidcUserAuthority = (OidcUserAuthority) authority;
                OidcUserInfo      userInfo          = oidcUserAuthority.getUserInfo();

                // Check if the roles are contained in the REALM_ACCESS_CLAIM or the groups
                // claim from Keycloak
                if (userInfo.hasClaim(REALM_ACCESS_CLAIM)) {
                    // Extract the roles from the REALM_ACCESS_CLAIM
                    Map<String, Object> realmAccess = userInfo.getClaimAsMap(REALM_ACCESS_CLAIM);
                    Collection<?>       rawRoles    = (Collection<?>) realmAccess.get(ROLES_CLAIM);
                    Collection<String>  roles       = rawRoles.stream().filter(String.class::isInstance)
                            .map(String.class::cast).toList();

                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                } else if (userInfo.hasClaim(GROUPS)) {
                    // Get the roles from the GROUPS claim
                    Collection<String> roles = userInfo.getClaimAsStringList(GROUPS);

                    // Add the roles to SpringSecurity
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                }
            } else {
                OAuth2UserAuthority oAuth2UserAuthority = (OAuth2UserAuthority) authority;
                Map<String, Object> userAttributes      = oAuth2UserAuthority.getAttributes();
                Map<String, Object> realmAccess         = convertAttributeToMapIfPossible(
                        userAttributes.get(REALM_ACCESS_CLAIM));

                if (realmAccess != null) {
                    Object rawRoles = realmAccess.get(ROLES_CLAIM);

                    if (rawRoles instanceof Collection<?> rawRolesCollection) {
                        Collection<String> roles = rawRolesCollection.stream().filter(String.class::isInstance)
                                .map(String.class::cast).toList();

                        mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                    }
                }
            }
            return mappedAuthorities;
        };
    }

    private Map<String, Object> convertAttributeToMapIfPossible(Object suspectedMap) {
        final var newMap = new HashMap<String, Object>();
        if (suspectedMap instanceof Map<?, ?> claimsMap) {
            for (var entry : claimsMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    newMap.put(key, entry.getValue());
                }
            }
        }
        return newMap;
    }

    Collection<SimpleGrantedAuthority> generateAuthoritiesFromClaim(Collection<String> roles) {
        // Returns the roles from OAuth2 and add the prefix ROLE_
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
