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

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.rsocket.metadata.AuthMetadataCodec;
import io.sapl.node.auth.UserLookupService;
import io.sapl.server.pdpcontroller.RSocketConnectionAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Provides the {@link RSocketConnectionAuthenticator} bean for the protobuf
 * RSocket endpoint. Mirrors the HTTP security configuration: supports basic
 * auth and API key (bearer token) using the same user store.
 * <p>
 * The authenticator bean is only created when
 * {@code sapl.pdp.rsocket.enabled=true}. If {@code io.sapl.node.allow-no-auth}
 * is true, all connections are accepted with the default PDP ID.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RSocketSecurityConfiguration {

    private static final String ERROR_AUTH_FAILED     = "Authentication failed.";
    private static final String ERROR_INVALID_API_KEY = "Invalid API key.";
    private static final String ERROR_NO_CREDENTIALS  = "No authentication credentials in setup frame.";
    private static final String ERROR_UNKNOWN_USER    = "Unknown user: %s.";

    private final SaplNodeProperties properties;
    private final UserLookupService  userLookupService;
    private final PasswordEncoder    passwordEncoder;

    @Bean
    @Nullable
    RSocketConnectionAuthenticator rsocketConnectionAuthenticator(
            @Value("${sapl.pdp.rsocket.enabled:false}") boolean rsocketEnabled) {
        if (!rsocketEnabled) {
            return null;
        }
        if (properties.isAllowNoAuth()) {
            log.warn("RSocket endpoint accepts unauthenticated connections");
            return setup -> Mono.just(properties.getDefaultPdpId());
        }
        return setup -> Mono.defer(() -> {
            val metadata = setup.metadata();
            if (metadata.readableBytes() == 0) {
                return Mono.error(new BadCredentialsException(ERROR_NO_CREDENTIALS));
            }
            val metadataBuf = Unpooled.wrappedBuffer(metadata.nioBuffer());
            try {
                val authType = AuthMetadataCodec.readWellKnownAuthType(metadataBuf);
                return switch (authType) {
                case SIMPLE -> authenticateBasic(metadataBuf);
                case BEARER -> authenticateBearer(metadataBuf);
                default     -> Mono.error(new BadCredentialsException(ERROR_AUTH_FAILED));
                };
            } catch (Exception e) {
                return Mono.error(new BadCredentialsException(ERROR_AUTH_FAILED, e));
            }
        });
    }

    private Mono<String> authenticateBasic(ByteBuf metadata) {
        val usernameBuf = AuthMetadataCodec.readUsername(metadata);
        val passwordBuf = AuthMetadataCodec.readPassword(metadata);
        val username    = usernameBuf.toString(StandardCharsets.UTF_8);
        val password    = passwordBuf.toString(StandardCharsets.UTF_8);
        log.debug("RSocket basic auth attempt: username='{}', password length={}", username, password.length());
        val userOpt = userLookupService.findByBasicUsername(username);
        if (userOpt.isEmpty()) {
            return Mono.error(new BadCredentialsException(ERROR_UNKNOWN_USER.formatted(username)));
        }
        val user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getBasic().getSecret())) {
            return Mono.error(new BadCredentialsException(ERROR_AUTH_FAILED));
        }
        log.debug("RSocket basic auth: user={}, pdpId={}", user.getId(), user.getPdpId());
        return Mono.just(user.getPdpId());
    }

    private Mono<String> authenticateBearer(ByteBuf metadata) {
        val tokenChars = AuthMetadataCodec.readBearerTokenAsCharArray(metadata);
        val token      = new String(tokenChars);
        val userOpt    = userLookupService.findByApiKey(token);
        if (userOpt.isEmpty()) {
            return Mono.error(new BadCredentialsException(ERROR_INVALID_API_KEY));
        }
        val user = userOpt.get();
        log.debug("RSocket API key auth: user={}, pdpId={}", user.getId(), user.getPdpId());
        return Mono.just(user.getPdpId());
    }

}
