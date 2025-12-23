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
package io.sapl.server.ce.security;

import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.apikey.ApiKeyPayloadExchangeAuthenticationConverterService;
import io.sapl.server.ce.security.apikey.ApiKeyReactiveAuthenticationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.PayloadInterceptorOrder;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.rsocket.authentication.AuthenticationPayloadExchangeConverter;
import org.springframework.security.rsocket.authentication.AuthenticationPayloadInterceptor;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@EnableRSocketSecurity
@RequiredArgsConstructor
@EnableReactiveMethodSecurity
@Conditional(SetupFinishedCondition.class)
public class RSocketSecurityConfig {

    @Value("${io.sapl.server.allowBasicAuth:#{true}}")
    private boolean allowBasicAuth;
    @Value("${io.sapl.server.allowApiKeyAuth:#{true}}")
    private boolean allowApiKeyAuth;

    @Value("${io.sapl.server.allowOauth2Auth:#{false}}")
    private boolean allowOauth2Auth;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    private String jwtIssuerURI;

    private final PasswordEncoder                                     passwordEncoder;
    private final ApiKeyPayloadExchangeAuthenticationConverterService apiKeyPayloadExchangeAuthenticationConverterService;

    private static void customize(RSocketSecurity.AuthorizePayloadsSpec spec) {
        spec.anyRequest().authenticated().anyExchange().permitAll();
    }

    /**
     * The PayloadSocketAcceptorInterceptor Bean (rsocketPayloadAuthorization)
     * configures the Security Filter Chain for Rsocket Payloads. Supported
     * Authentication Methods are: NoAuth, BasicAuth, Oauth2 (jwt) and ApiKey.
     */
    @Bean
    PayloadSocketAcceptorInterceptor rsocketPayloadAuthorization(RSocketSecurity security,
            UserDetailsService userDetailsService) {
        security = security.authorizePayload(RSocketSecurityConfig::customize);

        // Configure Basic Authentication
        UserDetailsRepositoryReactiveAuthenticationManager simpleManager = null;
        if (allowBasicAuth) {
            log.info("configuring BasicAuth for RSocket authentication");
            simpleManager = new UserDetailsRepositoryReactiveAuthenticationManager(
                    username -> Mono.just(userDetailsService.loadUserByUsername(username)));
            simpleManager.setPasswordEncoder(passwordEncoder);
        }

        // Configure Oauth2 Authentication
        JwtReactiveAuthenticationManager jwtManager = null;
        if (allowOauth2Auth) {
            log.info("configuring Oauth2 authentication with jwtIssuerURI: " + jwtIssuerURI);
            jwtManager = new JwtReactiveAuthenticationManager(ReactiveJwtDecoders.fromIssuerLocation(jwtIssuerURI));
        }

        final var finalSimpleManager = simpleManager;
        final var finalJwtManager    = jwtManager;
        final var auth               = new AuthenticationPayloadInterceptor(authentication -> {
                                         if (finalSimpleManager != null
                                                 && authentication instanceof UsernamePasswordAuthenticationToken) {
                                             return finalSimpleManager.authenticate(authentication);
                                         } else if (finalJwtManager != null
                                                 && authentication instanceof BearerTokenAuthenticationToken) {
                                             return finalJwtManager.authenticate(authentication);
                                         } else {
                                             throw new IllegalArgumentException("Unsupported Authentication Type "
                                                     + authentication.getClass().getSimpleName());
                                         }
                                     });

        auth.setAuthenticationConverter(new AuthenticationPayloadExchangeConverter());
        auth.setOrder(PayloadInterceptorOrder.AUTHENTICATION.getOrder());
        security.addPayloadInterceptor(auth);

        // Configure ApiKey authentication
        if (allowApiKeyAuth) {
            log.info("configuring ApiKey for RSocket authentication");
            final var manager           = new ApiKeyReactiveAuthenticationManager();
            final var apikeyInterceptor = new AuthenticationPayloadInterceptor(manager);
            apikeyInterceptor.setAuthenticationConverter(apiKeyPayloadExchangeAuthenticationConverterService);
            apikeyInterceptor.setOrder(PayloadInterceptorOrder.AUTHENTICATION.getOrder());
            security.addPayloadInterceptor(apikeyInterceptor);
        }

        return security.build();

    }
}
