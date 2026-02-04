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

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import io.sapl.node.SaplNodeProperties;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Mono;

/**
 * Converts a JWT to a SaplJwtAuthenticationToken, extracting the pdpId from
 * claims.
 */
@RequiredArgsConstructor
public class SaplJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private static final String                       ERROR_MISSING_PDP_ID_CLAIM = "JWT token missing required claim: %s";
    private static final List<SimpleGrantedAuthority> PDP_CLIENT_AUTHORITIES     = List
            .of(new SimpleGrantedAuthority("ROLE_PDP_CLIENT"));

    private final SaplNodeProperties properties;

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        val pdpIdClaim = properties.getOauth().getPdpIdClaim();
        val pdpIdValue = jwt.getClaimAsString(pdpIdClaim);

        if (pdpIdValue == null || pdpIdValue.isBlank()) {
            if (properties.isRejectOnMissingPdpId()) {
                return Mono.error(new InvalidBearerTokenException(ERROR_MISSING_PDP_ID_CLAIM.formatted(pdpIdClaim)));
            }
            return Mono.just(new SaplJwtAuthenticationToken(jwt, PDP_CLIENT_AUTHORITIES, properties.getDefaultPdpId()));
        }

        return Mono.just(new SaplJwtAuthenticationToken(jwt, PDP_CLIENT_AUTHORITIES, pdpIdValue));
    }

}
