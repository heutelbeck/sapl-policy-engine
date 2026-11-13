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
package io.sapl.attributeapi.attributes.backend;

import io.sapl.api.model.Value;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;

import static io.sapl.api.shared.NameValidator.requireValidName;

/**
 * Identifies an attribute in a store. The name must be a valid SAPL attribute name. Arguments and entity are optional.
 *
 * @param entity the optional entity of an attribute
 * @param name the mandatory attribute name of an attribute.
 * @param arguments the optional attribute arguments.
 */
public record AttributeKey(@Nullable Value entity, @NonNull String name, @NonNull List<Value> arguments) {
    /**
     * Creates the attribute key and validates the attribute name to be compliant with SAPL's regex.
     *
     * @param entity The optional entity of the attribute key.
     * @param name The attribute name of the attribute key.
     * @param arguments The optional arguments of the attribute key.
     */
    public AttributeKey {
        requireValidName(name);
        arguments = List.copyOf(arguments);
    }
}
