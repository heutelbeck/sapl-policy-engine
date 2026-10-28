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
package io.sapl.extensions.attributerepository;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.pdp.configuration.source.PdpIdValidator;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Factory class to build a specific attribute repository using the interface
 * {@link io.sapl.attributes.broker.AttributeRepository}. This class is called
 * by {@link RoutingAttributeRepository} once per pdp id whenever a new
 * configuration arrives.
 * <p>
 * The backends are not part of the PDP. Each backend is a module with an
 * {@link AttributeRepositoryProvider} that is found via the
 * {@link ServiceLoader}. The factory class selects the provider by the
 * {@code type} field of the configuration. The provider validates the remaining
 * fields and builds the repository.
 *
 * @since 4.2.0
 */
@Slf4j
@UtilityClass
public class AttributeRepositoryFactory {
    private static final String ERROR_UNKNOWN_TYPE  = "attributerepository.type='%s' for pdpId '%s' is not "
            + "supported. Available types: %s. Add the module of the backend to the classpath.";
    private static final String WARN_DUPLICATE_TYPE = "Attribute repository type '{}' is provided twice. {} is ignored.";

    private static final String TYPE_FIELD = "type";

    // Loaded only once with the existing JARs.
    private static final Map<String, AttributeRepositoryProvider> PROVIDERS = loadProviders();

    /**
     * Creates the specific {@code AttributeRepository} from a configuration for a
     * specific pdp id.
     *
     * @param config The given configuration block to build the attribute
     * repository.
     * @param pdpId The pdp id that the repository is built for.
     * @return The repository of the provider that matches the {@code type} field of
     * {@code config}.
     * @throws IllegalStateException if {@code type} is missing or no provider for
     * it is on the classpath.
     */
    public AttributeRepository create(ObjectValue config, String pdpId) {
        PdpIdValidator.validatePdpId(pdpId);
        val type     = stringValue(config, TYPE_FIELD);
        val provider = providerFor(type);

        if (provider == null) {
            throw new IllegalStateException(ERROR_UNKNOWN_TYPE.formatted(type, pdpId, PROVIDERS.keySet()));
        }

        return provider.create(config, pdpId);
    }

    /**
     * Validates if a given config matches one of the available attribute repository
     * types and contains the required fields of that type.
     *
     * @param config The configuration block that is sent by the extension
     * processor.
     * @return {@code true} if a provider for the type exists and the required
     * fields exist.
     */
    public boolean validate(ObjectValue config) {
        val provider = providerFor(stringValue(config, TYPE_FIELD));
        return provider != null && provider.validate(config);
    }

    /**
     * Types of the providers that are found on the classpath.
     *
     * @return A key set of available provider types
     */
    public Set<String> availableTypes() {
        return PROVIDERS.keySet();
    }

    private String stringValue(ObjectValue obj, String key) {
        val keyValue = obj.get(key);
        return keyValue instanceof TextValue(String value) ? value : null;
    }

    private AttributeRepositoryProvider providerFor(String type) {
        return type == null ? null : PROVIDERS.get(type.toLowerCase(Locale.ROOT));
    }

    private static Map<String, AttributeRepositoryProvider> loadProviders() {
        val providers = new HashMap<String, AttributeRepositoryProvider>();
        for (val provider : ServiceLoader.load(AttributeRepositoryProvider.class)) {
            val type = provider.type().toLowerCase(Locale.ROOT);
            if (providers.putIfAbsent(type, provider) != null) {
                log.warn(WARN_DUPLICATE_TYPE, type, provider.getClass().getName());
            }
        }
        return Map.copyOf(providers);
    }
}
