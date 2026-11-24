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

import java.util.List;

/**
 * Root interface for a SAPL document AST.
 * <p>
 * A SAPL document is either a single {@link Policy} or a {@link PolicySet}.
 */
public sealed interface SaplDocument extends AstNode permits Policy, PolicySet {

    /**
     * @return the import statements, empty list if none
     */
    List<Import> imports();

    /**
     * @return the source location covering the entire document
     */
    SourceLocation location();

    /**
     * @return the source range of the introductory {@code policy} or {@code set}
     * keyword
     */
    default SourceLocation declarationLocation() {
        var source        = location();
        int keywordLength = switch (this) {
                          case Policy ignored    -> "policy".length();
                          case PolicySet ignored -> "set".length();
                          };
        int length        = Math.min(keywordLength, source.end() - source.start());
        int endColumn     = source.column() > 0 ? source.column() + length : source.column();
        return new SourceLocation(source.documentName(), source.documentSource(), source.start(),
                source.start() + length, source.line(), source.column(), source.line(), endColumn);
    }

    /**
     * @return the name of the policy or policy set
     */
    String name();
}
