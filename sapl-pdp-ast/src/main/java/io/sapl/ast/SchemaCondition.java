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
 * Schema condition statement: schema validations that must pass.
 * <p>
 * This statement is automatically generated from schema declarations
 * and inserted as the first statement in a policy body.
 *
 * @param schemas the schema statements
 * @param location metadata location covering the schema block
 */
public record SchemaCondition(@NonNull List<SchemaStatement> schemas, @NonNull SourceLocation location)
        implements Statement {

    public SchemaCondition {
        schemas = List.copyOf(schemas);
    }
}
