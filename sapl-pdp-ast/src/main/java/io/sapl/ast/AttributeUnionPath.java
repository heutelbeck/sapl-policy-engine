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
package io.sapl.ast;

import io.sapl.api.model.SourceLocation;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.NonNull;

import java.util.List;

/**
 * Attribute union path element: {@code ["key1", "key2"]}
 *
 * @param keys the attribute keys to select
 * @param location source location
 */
public record AttributeUnionPath(@NonNull List<String> keys, @NonNull SourceLocation location) implements PathElement {
    public AttributeUnionPath {
        keys = List.copyOf(keys);
        if (keys.size() < 2) {
            throw new SaplCompilerException("Attribute union requires at least 2 keys", location);
        }
    }
}
