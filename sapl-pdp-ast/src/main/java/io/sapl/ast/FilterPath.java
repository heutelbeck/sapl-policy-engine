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
import lombok.NonNull;

import java.util.List;

/**
 * A path expression for filter targets, starting implicitly from {@code @}.
 * <p>
 * For example, in {@code @.items[*].price}, the elements would be:
 * [KeyPath("items"), WildcardPath, KeyPath("price")]
 * <p>
 * An empty elements list means the whole value ({@code @}).
 *
 * @param elements the path elements (empty for whole value)
 * @param location metadata location
 */
public record FilterPath(@NonNull List<PathElement> elements, @NonNull SourceLocation location) implements AstNode {
    public FilterPath {
        elements = List.copyOf(elements);
    }

    /**
     * @return true if this path targets the whole value (empty path, just @)
     */
    public boolean isWholeValue() {
        return elements.isEmpty();
    }
}
