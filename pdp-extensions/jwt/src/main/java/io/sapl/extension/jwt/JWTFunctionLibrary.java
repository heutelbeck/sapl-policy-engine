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
package io.sapl.extension.jwt;

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
import lombok.SneakyThrows;

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
    static final String DESCRIPTION = """
            Functions for evaluating JSON Web Tokens.
            The contents of the token are returned without verifying the token's validity.""";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectMapper mapper;

    @Function(docs = """
            ```parseJwt(TEXT rawToken)```:
            This function parses the raw encoded JWT Token and converts it into a SAPL value with the decoded contents
            of the token. The token is not validated by this function. Use the JWT PIPs/Attributes for this purpose,
            as the validity is time dependent.

            **Example:**

            ```
            policy "jwt example"
            permit
            where
              var rawToken = "eyJraWQiOiI3ZGRkYzMwNy1kZGE0LTQ4ZjUtYmU1Yi00MDZlZGFmYjc5ODgiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6Im1pc2thdG9uaWMtY2xpZW50IiwibmJmIjoxNjM1MjUxNDE1LCJzY29wZSI6WyJmYWN1bHR5LnJlYWQiLCJib29rcy5yZWFkIl0sImlzcyI6Imh0dHA6XC9cL2F1dGgtc2VydmVyOjkwMDAiLCJleHAiOjE2MzUyNTE3MTUsImlhdCI6MTYzNTI1MTQxNX0.V0-bViu4pFVufOzrn8yTQO9TnDAbE-qEKW8DnBKNLKCn2BlrQHbLYNSCpc4RdFU-cj32OwNn3in5cFPtiL5CTiD-lRXxnnc5WaNPNW2FchYag0zc252UdfV0Hs2sOAaNJ8agJ_uv0fFupMRS340gNDFFZthmjhTrDHGErZU7qxc1Lk2NF7-TGngre66-5W3NZzBsexkDO9yDLP11StjF63705juPFL2hTdgAIqLpsIOMwfrgoAsl0-6P98ecRwtGZKK4rEjUxBwghxCu1gm7eZiYoet4K28wPoBzF3hso4LG789N6GJt5HBIKpob9Q6G1ZJhMgieLeXH__9jvw1e0w";
              "books.read" in jwt.parseJwt(rawToken).payload.scope;
            ```

            In this case, the statement ```"books.read" in jwt.parseJwt(rawToken).payload.scope;``` will evaluate to
            ```true```, as the the result of the ```parseJwt``` function would be:
            ```
            {
              "header": {
                          "kid":"7dddc307-dda4-48f5-be5b-406edafb7988",
                          "alg":"RS256"
                        },
              "payload": {
                           "sub":"user1",
                           "aud":"miskatonic-client",
                           "nbf":"2021-10-26T12:30:15Z",
                           "scope":["faculty.read","books.read"],
                           "iss":"http://auth-server:9000",
                           "exp":"2021-10-26T12:35:15Z",
                           "iat":"2021-10-26T12:30:15Z"
                         }
            }
            ```
            """)
    @SneakyThrows
    public Val parseJwt(@Text Val rawToken) {
        final var signedJwt = SignedJWT.parse(rawToken.getText());
        final var jsonToken = JSON.objectNode();
        final var payload   = mapper.convertValue(signedJwt.getPayload().toJSONObject(), JsonNode.class);
        ifPresentReplaceEpocFieldWithIsoTime(payload, "nbf");
        ifPresentReplaceEpocFieldWithIsoTime(payload, "exp");
        ifPresentReplaceEpocFieldWithIsoTime(payload, "iat");
        jsonToken.set("header", mapper.convertValue(signedJwt.getHeader().toJSONObject(), JsonNode.class));
        jsonToken.set("payload", payload);
        return Val.of(jsonToken);
    }

    private void ifPresentReplaceEpocFieldWithIsoTime(JsonNode payload, String key) {
        if (!(payload.isObject() && payload.has(key) && payload.get(key).isNumber()))
            return;

        final var epocSeconds = payload.get(key).asLong();
        final var isoString   = Instant.ofEpochSecond(epocSeconds).toString();

        ((ObjectNode) payload).set(key, JSON.textNode(isoString));
    }

}
