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
package io.sapl.functions.libraries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jwt.SignedJWT;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import lombok.RequiredArgsConstructor;

import java.text.ParseException;
import java.time.Instant;

/**
 * Function library for parsing JSON Web Tokens without validation.
 * <p>
 * This library provides fast, unvalidated access to JWT contents for use in
 * policy target expressions. Target
 * expressions require rapid evaluation for policy indexing and selection, which
 * prohibits external service calls needed
 * for signature verification.
 * <p>
 * <strong>Security Warning:</strong> Functions in this library return JWT
 * contents without verifying signatures or
 * validating claims. For secure authorization decisions, always use JWT
 * attributes from the JWT Policy Information
 * Point, which perform proper signature verification and time-based validation.
 * <p>
 * <strong>Recommended Pattern:</strong> Use functions in target expressions for
 * fast policy selection, then validate
 * using JWT PIP attributes in policy bodies:
 *
 * <pre>
 * policy "secure-jwt-access"
 * permit action == "read"
 *   where "admin" in jwt.parseJwt(subject.token).payload.roles;
 * where
 *   subject.token.&lt;jwt.valid&gt;;
 * </pre>
 *
 * @see io.sapl.attributes.pips.jwt.JWTPolicyInformationPoint
 */
@RequiredArgsConstructor
@FunctionLibrary(name = JWTFunctionLibrary.NAME, description = JWTFunctionLibrary.DESCRIPTION, libraryDocumentation = JWTFunctionLibrary.LIBRARY_DOCUMENTATION)
public class JWTFunctionLibrary {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final ObjectMapper           mapper;

    static final String NAME        = "jwt";
    static final String DESCRIPTION = "Functions for parsing JSON Web Tokens. Contents are returned without validation.";

    static final String LIBRARY_DOCUMENTATION = """
            # JWT Function Library

            Provides fast, unvalidated parsing of JSON Web Tokens for use in policy target expressions.

            ## Security Model

            **Functions in this library DO NOT validate JWT signatures or claims.** They return token
            contents as-is without verification. This design enables fast policy selection through target
            expressions, which cannot call external services required for proper JWT validation.

            For secure authorization decisions, combine this library with the JWT Policy Information Point:

            * **Function Library** (jwt.parseJwt): Fast parsing for target expressions and policy selection
            * **JWT PIP** (jwt.valid, jwt.validity): Secure validation with signature verification

            ## Recommended Pattern

            Use functions for quick filtering, then validate with PIP attributes:

            ```sapl
            policy "secure-resource-access"
            permit action.method == "GET"
              where "documents:read" in jwt.parseJwt(subject.token).payload.scope;
            where
              subject.token.<jwt.valid>;
            ```

            This pattern provides:
            1. Fast policy selection via unvalidated token parsing in target
            2. Secure authorization via validated token in policy body

            ## JWT PIP Integration

            The JWT Policy Information Point provides validated attributes:

            * `<jwt.valid>`: Boolean indicating current token validity
            * `<jwt.validity>`: Validity state (VALID, EXPIRED, IMMATURE, UNTRUSTED, etc.)

            PIP attributes are reactive streams that automatically trigger policy re-evaluation when
            tokens transition between states (immature -> valid -> expired).

            See JWT PIP documentation for configuration of public key servers and trusted key whitelists.

            ## Example

            Target expression for policy selection:
            ```sapl
            policy "api-scope-filter"
            permit action.api == "documents"
              where "docs:write" in jwt.parseJwt(subject.credentials).payload.scope;
            where
              var token = subject.credentials;
              token.<jwt.validity> == "VALID";
            ```

            The target expression quickly filters relevant policies by checking scopes without validation.
            The policy body then validates the token signature and time claims before granting access.
            """;

    /**
     * Parses a JWT and returns its decoded header and payload without validation.
     *
     * @param rawToken
     * the Base64-encoded JWT string
     *
     * @return Value containing header and payload, or error if parsing fails
     */
    @Function(docs = """
            ```parseJwt(TEXT rawToken)```: Parses the raw encoded JWT token and converts it into a SAPL
            value with the decoded header and payload. The token is NOT validated by this function.
            Use JWT PIP attributes for validation.

            Returns an object with structure:
            ```json
            {
              "header": { "kid": "...", "alg": "..." },
              "payload": { "sub": "...", "exp": "...", ... }
            }
            ```

            Time claims (nbf, exp, iat) are converted from epoch seconds to ISO-8601 timestamps.

            **Example:**
            ```sapl
            policy "check-token-scope"
            permit
              where "admin" in jwt.parseJwt(subject.token).payload.roles;
            where
              subject.token.<jwt.valid>;
            ```
            """)
    public Value parseJwt(TextValue rawToken) {
        try {
            var signedJwt = SignedJWT.parse(rawToken.value());
            var jsonToken = JSON.objectNode();
            var payload   = mapper.convertValue(signedJwt.getPayload().toJSONObject(), JsonNode.class);

            ifPresentReplaceEpochFieldWithIsoTime(payload, "nbf");
            ifPresentReplaceEpochFieldWithIsoTime(payload, "exp");
            ifPresentReplaceEpochFieldWithIsoTime(payload, "iat");

            jsonToken.set("header", mapper.convertValue(signedJwt.getHeader().toJSONObject(), JsonNode.class));
            jsonToken.set("payload", payload);

            return ValueJsonMarshaller.fromJsonNode(jsonToken);
        } catch (ParseException exception) {
            return new ErrorValue("Failed to parse JWT: " + exception.getMessage());
        } catch (Exception exception) {
            return new ErrorValue("Error processing JWT: " + exception.getMessage());
        }
    }

    private void ifPresentReplaceEpochFieldWithIsoTime(JsonNode payload, String key) {
        if (!(payload.isObject() && payload.has(key) && payload.get(key).isNumber())) {
            return;
        }

        var epochSeconds = payload.get(key).asLong();
        var isoString    = Instant.ofEpochSecond(epochSeconds).toString();

        ((ObjectNode) payload).set(key, JSON.textNode(isoString));
    }

}
