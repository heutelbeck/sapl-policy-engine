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
 * Record to set a {@code AttributeKey} used within the API and is part of {@link AttributeEntry}.
 * Checks if {@code name} has a valid attribute name for the SAPL engine and {@code arguments}
 * returns an immutable list of arguments.
 */
public record AttributeKey(@Nullable Value entity, @NonNull String name, @NonNull List<Value> arguments) {
    public AttributeKey {
        requireValidName(name);
        arguments = List.copyOf(arguments);
    }
}
