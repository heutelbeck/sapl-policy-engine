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
 * Import statement: {@code import lib.path.functionName as alias}
 *
 * @param libraryPath the library path components
 * @param functionName the function name to import
 * @param alias the alias to use, or null if no alias
 * @param location source location
 */
public record Import(
        @NonNull List<String> libraryPath,
        @NonNull String functionName,
        String alias,
        @NonNull SourceLocation location) implements AstNode {

    public Import {
        libraryPath = List.copyOf(libraryPath);
    }

    /**
     * @return the effective name to use (alias if present, otherwise function
     * name)
     */
    public String effectiveName() {
        return alias != null ? alias : functionName;
    }

    /**
     * @return the fully qualified name of the imported function
     */
    public String fullName() {
        if (libraryPath.isEmpty()) {
            return functionName;
        }
        return String.join(".", libraryPath) + "." + functionName;
    }

}
