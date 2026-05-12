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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Adds {@code components.securitySchemes} entries and a global
 * {@code security} requirement to the OpenAPI document, conditional on which
 * authentication mechanisms the node has enabled. Lets Scalar's "Authorize"
 * panel offer the schemes the operator actually accepts.
 * <p>
 * Three schemes may appear:
 * <ul>
 * <li>{@code basic} when {@code io.sapl.node.allow-basic-auth=true}.</li>
 * <li>{@code saplApiKey} when {@code io.sapl.node.allow-api-key-auth=true};
 * declared as HTTP bearer with the {@code sapl_<token>} format hint.</li>
 * <li>{@code oauth2} when {@code io.sapl.node.allow-oauth2-auth=true} and
 * the JWT issuer URI is set; declared as {@code oauth2} with explicit
 * {@code authorizationCode} flow URLs resolved from the issuer's OIDC
 * discovery document. Scalar 0.6.34 does not propagate
 * {@code x-scalar-redirect-uri} into its runtime state for plain
 * {@code openIdConnect} schemes, so the explicit {@code oauth2} variant is
 * required for the redirect URI to reach the auth-code flow.</li>
 * </ul>
 * The OAuth2 scheme can be configured with:
 * <ul>
 * <li>{@code io.sapl.node.scalar.oauth-client-id} - client id Scalar uses
 * for the auth-code flow.</li>
 * <li>{@code io.sapl.node.scalar.oauth-redirect-uri} - redirect URI
 * registered at the issuer. Defaults to {@code /scalar}, which Scalar
 * resolves relative to the current page origin.</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class OpenApiSecuritySchemesCustomizer {

    private static final String SCHEME_BASIC   = "basic";
    private static final String SCHEME_API_KEY = "saplApiKey";
    private static final String SCHEME_OAUTH2  = "oauth2";

    private static final String OIDC_DISCOVERY_PATH = "/.well-known/openid-configuration";
    private static final String DEFAULT_REDIRECT    = "/scalar";

    private final SaplNodeProperties saplNodeProperties;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    @Nullable
    private String jwtIssuerUri;

    @Value("${io.sapl.node.scalar.oauth-client-id:#{null}}")
    @Nullable
    private String scalarOauthClientId;

    @Value("${io.sapl.node.scalar.oauth-redirect-uri:" + DEFAULT_REDIRECT + "}")
    private String scalarOauthRedirectUri;

    private final AtomicReference<SecurityScheme> cachedOauth2Scheme = new AtomicReference<>();

    @Bean
    OpenApiCustomizer securitySchemesCustomizer() {
        return openApi -> {
            ensureComponents(openApi);
            val components = openApi.getComponents();
            if (openApi.getSecurity() == null) {
                openApi.setSecurity(new ArrayList<>());
            }
            val security = openApi.getSecurity();

            if (saplNodeProperties.isAllowBasicAuth()) {
                components.addSecuritySchemes(SCHEME_BASIC, basicAuthScheme());
                security.add(new SecurityRequirement().addList(SCHEME_BASIC));
            }
            if (saplNodeProperties.isAllowApiKeyAuth()) {
                components.addSecuritySchemes(SCHEME_API_KEY, saplApiKeyScheme());
                security.add(new SecurityRequirement().addList(SCHEME_API_KEY));
            }
            if (saplNodeProperties.isAllowOauth2Auth() && jwtIssuerUri != null) {
                val oauth = resolveOauth2Scheme();
                if (oauth != null) {
                    components.addSecuritySchemes(SCHEME_OAUTH2, oauth);
                    security.add(new SecurityRequirement().addList(SCHEME_OAUTH2));
                }
            }
        };
    }

    /**
     * Returns the OAuth2 scheme, computing it once on the first successful
     * resolution and caching the result for the lifetime of the bean. A
     * failed resolution leaves the cache empty so a transient issuer outage
     * at first request does not permanently disable the scheme.
     */
    private @Nullable SecurityScheme resolveOauth2Scheme() {
        val cached = cachedOauth2Scheme.get();
        if (cached != null) {
            return cached;
        }
        val resolved = oauth2Scheme(jwtIssuerUri, scalarOauthClientId, scalarOauthRedirectUri);
        if (resolved != null) {
            cachedOauth2Scheme.compareAndSet(null, resolved);
        }
        return resolved;
    }

    private static void ensureComponents(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
    }

    private static SecurityScheme basicAuthScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                .description("HTTP Basic with credentials configured under io.sapl.node.users[].basic.");
    }

    private static SecurityScheme saplApiKeyScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("sapl_<token>")
                .description("API key with the sapl_ prefix, sent as 'Authorization: Bearer sapl_<token>'. "
                        + "Generate via 'sapl generate apikey'.");
    }

    @Nullable
    private static SecurityScheme oauth2Scheme(String issuerUri, @Nullable String clientId, String redirectUri) {
        val discovery = fetchDiscovery(issuerUri);
        if (discovery == null) {
            return null;
        }
        val authUrl  = discovery.path("authorization_endpoint").asText(null);
        val tokenUrl = discovery.path("token_endpoint").asText(null);
        if (authUrl == null || tokenUrl == null) {
            log.warn("OIDC discovery at {} did not yield authorization_endpoint or token_endpoint", issuerUri);
            return null;
        }

        val scopes = new Scopes().addString("openid", "OpenID Connect identity");
        val flow   = new OAuthFlow().authorizationUrl(authUrl).tokenUrl(tokenUrl).scopes(scopes);
        flow.addExtension("x-scalar-redirect-uri", redirectUri);

        val description = clientId == null
                ? "OAuth2 auth-code + PKCE against issuer " + issuerUri
                        + ". Enter the client id in the Authorize dialog. The issuer must have a client "
                        + "registered with redirect URI " + redirectUri + "."
                : "OAuth2 auth-code + PKCE against issuer " + issuerUri + ". Client id '" + clientId
                        + "' is prefilled. The issuer must have this client registered with redirect URI " + redirectUri
                        + ".";
        return new SecurityScheme().type(SecurityScheme.Type.OAUTH2).flows(new OAuthFlows().authorizationCode(flow))
                .description(description);
    }

    @Nullable
    private static JsonNode fetchDiscovery(String issuerUri) {
        val url = issuerUri.endsWith("/") ? issuerUri + OIDC_DISCOVERY_PATH.substring(1)
                : issuerUri + OIDC_DISCOVERY_PATH;
        try (val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            val request  = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
            val response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("OIDC discovery {} returned HTTP {}", url, response.statusCode());
                return null;
            }
            return new ObjectMapper().readTree(response.body());
        } catch (Exception e) {
            log.warn("OIDC discovery {} failed: {}", url, e.getMessage());
            return null;
        }
    }

}
