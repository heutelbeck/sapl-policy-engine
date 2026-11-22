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
package io.sapl.api.model.jackson;

import java.io.Serial;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.NumberValueLimits;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.*;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson module that registers serializers and deserializers for SAPL types.
 * <p>
 * Register this module with an ObjectMapper to enable seamless JSON conversion
 * of SAPL Value types and PDP API types:
 *
 * <pre>{@code
 * JsonMapper mapper = SaplJacksonModule.jsonMapperBuilder().build();
 *
 * // Now serialize/deserialize SAPL types
 * Value value = Value.of("test");
 * String json = mapper.writeValueAsString(value);
 * Value parsed = mapper.readValue(json, Value.class);
 *
 * AuthorizationDecision decision = AuthorizationDecision.PERMIT;
 * String decisionJson = mapper.writeValueAsString(decision);
 * }</pre>
 * <p>
 * For a caller-owned JSON factory, set its maximum number length to at least
 * {@link NumberValueLimits#MAX_WIRE_NUMBER_LENGTH} to read the full decimal range.
 * For Spring Boot applications, this module can be auto-configured by creating
 * a bean.
 */
public class SaplJacksonModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a JSON mapper builder for SAPL wire values. The factory accepts the
     * exponent digits of a full-precision number before its token reaches the
     * SAPL deserializer.
     *
     * @return a builder with the SAPL module and numeric token limit
     */
    public static JsonMapper.Builder jsonMapperBuilder() {
        return JsonMapper.builder(ValueJsonMarshaller.JSON_FACTORY).addModule(new SaplJacksonModule());
    }

    /**
     * Creates a new SAPL Jackson module with all serializers and deserializers
     * registered.
     */
    public SaplJacksonModule() {
        super("SaplJacksonModule");

        // Value
        addSerializer(Value.class, new ValueSerializer());
        addDeserializer(Value.class, new ValueDeserializer());

        // AuthorizationSubscription
        addSerializer(AuthorizationSubscription.class, new AuthorizationSubscriptionSerializer());
        addDeserializer(AuthorizationSubscription.class, new AuthorizationSubscriptionDeserializer());

        // AuthorizationDecision
        addSerializer(AuthorizationDecision.class, new AuthorizationDecisionSerializer());
        addDeserializer(AuthorizationDecision.class, new AuthorizationDecisionDeserializer());

        // IdentifiableAuthorizationSubscription
        addSerializer(IdentifiableAuthorizationSubscription.class,
                new IdentifiableAuthorizationSubscriptionSerializer());
        addDeserializer(IdentifiableAuthorizationSubscription.class,
                new IdentifiableAuthorizationSubscriptionDeserializer());

        // IdentifiableAuthorizationDecision
        addSerializer(IdentifiableAuthorizationDecision.class, new IdentifiableAuthorizationDecisionSerializer());
        addDeserializer(IdentifiableAuthorizationDecision.class, new IdentifiableAuthorizationDecisionDeserializer());

        // MultiAuthorizationSubscription
        addSerializer(MultiAuthorizationSubscription.class, new MultiAuthorizationSubscriptionSerializer());
        addDeserializer(MultiAuthorizationSubscription.class, new MultiAuthorizationSubscriptionDeserializer());

        // MultiAuthorizationDecision
        addSerializer(MultiAuthorizationDecision.class, new MultiAuthorizationDecisionSerializer());
        addDeserializer(MultiAuthorizationDecision.class, new MultiAuthorizationDecisionDeserializer());

        // CombiningAlgorithm
        addSerializer(CombiningAlgorithm.class, new CombiningAlgorithmSerializer());
        addDeserializer(CombiningAlgorithm.class, new CombiningAlgorithmDeserializer());

        // PDPConfiguration
        addSerializer(PDPConfiguration.class, new PDPConfigurationSerializer());
        addDeserializer(PDPConfiguration.class, new PDPConfigurationDeserializer());
    }
}
