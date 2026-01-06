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
 * Unified filter op representing both simple and extended filters.
 * <p>
 * Simple filter: {@code base |- func(args)} or {@code base |- each func(args)}
 * <p>
 * Extended filter statements are transformed into nested FilterOperations:
 * {@code base |- { @.a: f1, @.b: f2 }} becomes
 * {@code FilterOperation(FilterOperation(base, @.a, f1), @.b, f2)}
 *
 * @param base the expression to filter
 * @param target the path to the target location (null for whole-value simple
 * filter)
 * @param function the filter function name
 * @param arguments function arguments (value being filtered is passed as first
 * arg)
 * @param each true if filter applies to each element of target array
 * @param location source location
 */
public record FilterOperation(
        @NonNull Expression base,
        FilterPath target,
        @NonNull QualifiedName function,
        @NonNull List<Expression> arguments,
        boolean each,
        @NonNull SourceLocation location) implements Expression {

    public FilterOperation {
        arguments = List.copyOf(arguments);
    }

    /**
     * @return true if this is a simple whole-value filter (no target path)
     */
    public boolean isSimpleWholeValue() {
        return target == null || target.isWholeValue();
    }

}
