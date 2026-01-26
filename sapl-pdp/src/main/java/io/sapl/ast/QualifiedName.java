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

import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.NonNull;

import java.io.Serializable;
import java.util.List;

/**
 * Qualified name for functions and attributes: {@code foo.bar.baz}
 *
 * @param parts the name parts, at least one required
 */
public record QualifiedName(@NonNull List<String> parts) implements Serializable {

    private static final String ERROR_CANNOT_BE_EMPTY = "Qualified name cannot be empty.";

    public QualifiedName {
        parts = List.copyOf(parts);
        if (parts.isEmpty()) {
            throw new SaplCompilerException(ERROR_CANNOT_BE_EMPTY);
        }
    }

    /**
     * @return the simple (last) part of the name
     */
    public String simple() {
        return parts.getLast();
    }

    /**
     * @return the full dot-separated name
     */
    public String full() {
        return String.join(".", parts);
    }

    /**
     * Creates a qualified name from parts.
     *
     * @param parts the name parts
     * @return a new qualified name
     */
    public static QualifiedName of(String... parts) {
        return new QualifiedName(List.of(parts));
    }

    /**
     * Creates a simple (single-part) qualified name.
     *
     * @param name the simple name
     * @return a new qualified name
     */
    public static QualifiedName simple(String name) {
        return new QualifiedName(List.of(name));
    }

    @Override
    public String toString() {
        return full();
    }

}
