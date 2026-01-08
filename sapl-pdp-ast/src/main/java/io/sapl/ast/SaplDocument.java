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
 * Root node of a SAPL document AST.
 *
 * @param imports import statements, empty list if none
 * @param schemas schema statements, empty list if none
 * @param element the policy or policy set
 * @param location source location covering the entire document
 */
public record SaplDocument(
        @NonNull List<Import> imports,
        @NonNull List<SchemaStatement> schemas,
        @NonNull PolicyElement element,
        @NonNull SourceLocation location) implements AstNode {

    public SaplDocument {
        imports = List.copyOf(imports);
        schemas = List.copyOf(schemas);
    }

    /**
     * @return the name of the contained policy or policy set
     */
    public String name() {
        return element.name();
    }

    /**
     * @return true if this document contains a policy set, false if single policy
     */
    public boolean isPolicySet() {
        return element instanceof PolicySet;
    }

}
