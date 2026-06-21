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
package io.sapl.spring.pdp.remote;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.val;

@Data
@Validated
@ConfigurationProperties(prefix = "io.sapl.pdp.remote")
public class RemotePDPProperties implements Validator {

    private static final String TYPE_HTTP                  = "http";
    private static final String TYPE_RSOCKET               = "rsocket";
    private static final String OAUTH2_CLIENT_REGISTRATION = "oauth2.clientRegistrationId";

    private static final String ERROR_INSECURE_CREDENTIAL_TRANSPORT = "Refusing to send remote PDP credentials over an unencrypted channel. Use https (or rsocket tls), or set io.sapl.pdp.remote.allow-insecure-http=true to permit this for local development only.";

    private boolean enabled = false;

    @NotEmpty
    private String  type               = TYPE_HTTP;
    private boolean ignoreCertificates = false;

    private String host = "";

    private int port = 7000;

    private String socketPath = "";

    private boolean tls = false;

    // Opt-in (development only) to send credentials over an unencrypted channel:
    // plain http for HTTP, or RSocket without TLS.
    private boolean allowInsecureHttp = false;

    private Duration keepAlive   = Duration.ofSeconds(20);
    private Duration maxLifeTime = Duration.ofSeconds(90);

    private String key    = "";
    private String secret = "";

    private String bearerToken = "";

    private boolean tokenRelay = false;

    private Oauth2 oauth2 = new Oauth2();

    /**
     * OAuth2 client_credentials grant configuration. References a Spring Security
     * OAuth2 client registration declared
     * via {@code spring.security.oauth2.client.registration.<id>.*}. Spring's
     * {@code OAuth2AuthorizedClientManager}
     * caches and refreshes the access token; on RSocket, expiry triggers a
     * reconnect with a fresh token.
     */
    @Data
    public static class Oauth2 {

        private String clientRegistrationId = "";

        private String principalName = "";
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return RemotePDPProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        val properties = (RemotePDPProperties) target;
        if (TYPE_HTTP.equals(properties.type)) {
            validateHttpHost(properties.host, errors);
        } else if (TYPE_RSOCKET.equals(properties.type)) {
            validateRSocketEndpoint(properties, errors);
        } else {
            errors.rejectValue("type", "type-invalid", new String[] { properties.type },
                    "Invalid type specified, valid values are \"http\" and \"rsocket\"");
        }
        validateAuthentication(properties, errors);
        validateCredentialTransportSecurity(properties, errors);
    }

    private void validateCredentialTransportSecurity(RemotePDPProperties properties, Errors errors) {
        if (!hasCredentials(properties) || isChannelEncrypted(properties) || properties.allowInsecureHttp) {
            return;
        }
        errors.reject("insecure-credential-transport", ERROR_INSECURE_CREDENTIAL_TRANSPORT);
    }

    private boolean hasCredentials(RemotePDPProperties properties) {
        return !properties.oauth2.clientRegistrationId.isEmpty() || properties.tokenRelay || !properties.key.isEmpty()
                || !properties.bearerToken.isEmpty();
    }

    private boolean isChannelEncrypted(RemotePDPProperties properties) {
        if (TYPE_HTTP.equals(properties.type)) {
            return properties.host.regionMatches(true, 0, "https://", 0, "https://".length());
        }
        // A unix domain socket is local IPC with no network hop, so it counts as a
        // secure channel.
        return properties.tls || properties.ignoreCertificates || !properties.socketPath.isEmpty();
    }

    private void validateHttpHost(String hostValue, Errors errors) {
        ValidationUtils.rejectIfEmpty(errors, "host", "requires-host", "host containing http url is required");
        if (!hostValue.isEmpty()) {
            try {
                new URI(hostValue).toURL();
            } catch (MalformedURLException | URISyntaxException | IllegalArgumentException ex) {
                errors.rejectValue("host", "host-invalid", new String[] { hostValue }, "host is not a valid URL");
            }
        }
    }

    private void validateRSocketEndpoint(RemotePDPProperties properties, Errors errors) {
        if (properties.socketPath.isEmpty()) {
            ValidationUtils.rejectIfEmpty(errors, "host", "requires-host",
                    "host (hostname) is required for rsocket transport unless socketPath is set");
            if (properties.port <= 0 || properties.port > 65535) {
                errors.rejectValue("port", "port-invalid", new String[] { String.valueOf(properties.port) },
                        "port must be between 1 and 65535 for rsocket transport");
            }
        }
        if (properties.tokenRelay) {
            errors.rejectValue("tokenRelay", "token-relay-rsocket",
                    "tokenRelay is not supported on the rsocket transport. "
                            + "RSocket authenticates once at connection setup. "
                            + "Use type=http if per-request user credential forwarding is required, "
                            + "or oauth2.client-registration-id for service-account JWTs over RSocket.");
        }
    }

    private void validateAuthentication(RemotePDPProperties properties, Errors errors) {
        val oauth2Set = !properties.oauth2.clientRegistrationId.isEmpty();
        if (oauth2Set) {
            validateOauth2Combinations(properties, errors);
            return;
        }
        if (properties.tokenRelay) {
            if (!properties.key.isEmpty() || !properties.bearerToken.isEmpty()) {
                errors.rejectValue("tokenRelay", "token-relay-conflict",
                        "token-relay cannot be combined with key/secret or bearer-token authentication");
            }
        } else if (properties.bearerToken.isEmpty() ^ properties.key.isEmpty()) {
            if (!properties.key.isEmpty()) {
                ValidationUtils.rejectIfEmpty(errors, "secret", "requires-secret", "\"secret\" must not be empty");
            }
        } else {
            errors.rejectValue("key", "key-invalid", new String[] { properties.key },
                    "At least one authentication mechanism needed: \"key\" and \"secret\", \"bearer-token\", "
                            + "\"token-relay\", or \"oauth2.client-registration-id\"");
        }
    }

    private void validateOauth2Combinations(RemotePDPProperties properties, Errors errors) {
        if (!properties.key.isEmpty() || !properties.secret.isEmpty()) {
            errors.rejectValue(OAUTH2_CLIENT_REGISTRATION, "oauth2-conflict-basic",
                    "oauth2.client-registration-id cannot be combined with key/secret authentication");
        }
        if (!properties.bearerToken.isEmpty()) {
            errors.rejectValue(OAUTH2_CLIENT_REGISTRATION, "oauth2-conflict-bearer",
                    "oauth2.client-registration-id cannot be combined with bearer-token authentication");
        }
        if (properties.tokenRelay) {
            errors.rejectValue(OAUTH2_CLIENT_REGISTRATION, "oauth2-conflict-token-relay",
                    "oauth2.client-registration-id cannot be combined with token-relay authentication");
        }
    }
}
