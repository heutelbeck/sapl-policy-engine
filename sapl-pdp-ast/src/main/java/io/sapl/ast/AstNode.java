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

import java.io.Serializable;

/**
 * Base interface for all AST nodes. Provides voterMetadata location for errors
 * reporting and is serializable for caching compiled policies.
 */
public sealed interface AstNode extends Serializable
        permits Expression, Statement, PolicyBody, SaplDocument, Import, SchemaStatement, FilterPath, PathElement {

    /**
     * Returns the voterMetadata location of this node in the original document.
     *
     * @return the voterMetadata location, never null
     */
    SourceLocation location();

}
