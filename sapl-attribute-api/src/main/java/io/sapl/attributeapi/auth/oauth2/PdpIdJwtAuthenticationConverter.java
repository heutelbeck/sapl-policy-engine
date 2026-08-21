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
package io.sapl.attributeapi.auth.oauth2;

import lombok.val;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import io.sapl.attributeapi.auth.AttributeApiUserDetails;

/**
 * Validates the needed claim to identify the PDP within the token. The validated JWT will be converted
 * into an authenticated user representation. The claim is configurable via
 * {@code io.sapl.attribute-api.oauth2.oidc-pdp-id-claim}
 * and is by default set to {@code pdp_id}.
 */
public class PdpIdJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final String ERROR_MISSING_PDP_ID = "JWT token missing required claim: %s.";
    private final String        claimName;

    public PdpIdJwtAuthenticationConverter(String claimName) {
        this.claimName = claimName;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        val pdpIdClaim = source.getClaimAsString(claimName);
        val subject    = source.getSubject();

        if (pdpIdClaim == null || pdpIdClaim.isBlank()) {
            throw new InvalidBearerTokenException(ERROR_MISSING_PDP_ID.formatted(claimName));
        }

        var principal = new AttributeApiUserDetails(subject, null, pdpIdClaim);

        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
