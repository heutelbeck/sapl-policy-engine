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
import io.sapl.attributes.broker.AttributeRepository;

/**
 * Service provider interface (SPI) for an attribute repository backend. Each backend has it own
 * module and registers the provider in
 * {@code META-INF/services/io.sapl.extensions.attributerepository.AttributeRepositoryProvider}.
 * The {@link AttributeRepositoryFactory} locates the providers and the PDP only loads the database of the backends that
 * are needed.
 *
 * @since 4.2.0
 */
public interface AttributeRepositoryProvider {
    /**
     * @return The value of the field {@code type} in the configuration. The type shows for which backend this
     * provider is responsible for, e.g. {@code redis}.
     */
    String type();

    /**
     * Checks if the configuration contains all fields that are needed to build the repository properly.
     *
     * @param config The configuration block of the extension.
     * @return {@code true} if the required field exists.
     */
    boolean validate(ObjectValue config);

    /**
     * Builds the repository for the given pdp id.
     *
     * @param config The configuration block of the extension.
     * @param pdpId The pdp id the repository is built for.
     * @return The attribute repository.
     */
    AttributeRepository create(ObjectValue config, String pdpId);
}
