/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.extension.jwt;

import java.text.ParseException;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jwt.SignedJWT;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.RequiredArgsConstructor;

/**
 * Library of functions for evaluating JSON Web Tokens (JWT)
 * <p>
 * Functions may be used in target expressions of SAPL policies. Since target
 * expressions need to be evaluated quickly for indexing and selecting policies,
 * functions are not allowed to call external services.
 * <p>
 * This prohibits functions from verifying digital signatures, as it would be
 * necessary to fetch public keys or certificates from external sources. <br>
 * The functions in this library therefore return information contained in JWTs
 * as-is, without verifying the token's validity.
 * <p>
 * For secure implementations, any function used in the target expression for
 * selecting a policy should therefore be repeated as attribute in the policy's
 * body, as JWT attributes are properly validated.
 */
@RequiredArgsConstructor
@FunctionLibrary(name = JWTFunctionLibrary.NAME, description = JWTFunctionLibrary.DESCRIPTION)
public class JWTFunctionLibrary {

    static final String NAME        = "jwt";
    static final String DESCRIPTION = "Functions for evaluating Json Web Tokens. The contents of the token are returned without verifying the token's validity.";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectMapper mapper;

    /**
     * @param rawToken a raw JWT token.
     * @return the contents of the JWT token as a Val.
     */
    @Function
    public Val parseJwt(@Text Val rawToken) {
        try {
            var signedJwt = SignedJWT.parse(rawToken.getText());
            var jsonToken = JSON.objectNode();
            var payload   = mapper.convertValue(signedJwt.getPayload().toJSONObject(), JsonNode.class);
            ifPresentReplaceEpocFieldWithIsoTime(payload, "nbf");
            ifPresentReplaceEpocFieldWithIsoTime(payload, "exp");
            ifPresentReplaceEpocFieldWithIsoTime(payload, "iat");
            jsonToken.set("header", mapper.convertValue(signedJwt.getHeader().toJSONObject(), JsonNode.class));
            jsonToken.set("payload", payload);
            return Val.of(jsonToken);
        } catch (ParseException e) {
            return Val.error(e);
        }
    }

    private void ifPresentReplaceEpocFieldWithIsoTime(JsonNode payload, String key) {
        if (!(payload.isObject() && payload.has(key) && payload.get(key).isNumber()))
            return;

        var epocSeconds = payload.get(key).asLong();
        var isoString   = Instant.ofEpochSecond(epocSeconds).toString();

        ((ObjectNode) payload).set(key, JSON.textNode(isoString));
    }

}
