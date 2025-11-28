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
package io.sapl.api.model.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.sapl.api.SaplVersion;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PDPConfiguration;

import java.io.Serial;

/**
 * Jackson module that registers serializers and deserializers for SAPL types.
 * <p>
 * Register this module with an ObjectMapper to enable seamless JSON conversion
 * of SAPL Value types and PDP API types:
 *
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper();
 * mapper.registerModule(new SaplJacksonModule());
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
 * For Spring Boot applications, this module can be auto-configured by creating
 * a bean.
 */
public class SaplJacksonModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

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

        // CombiningAlgorithm (case-insensitive deserialization)
        addDeserializer(CombiningAlgorithm.class, new CombiningAlgorithmDeserializer());

        // PDPConfiguration
        addSerializer(PDPConfiguration.class, new PDPConfigurationSerializer());
        addDeserializer(PDPConfiguration.class, new PDPConfigurationDeserializer());
    }
}
