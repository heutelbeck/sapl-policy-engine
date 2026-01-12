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

/**
 * Schema statement: {@code subject enforced? schema expression}
 *
 * @param element the subscription element this schema applies to
 * @param enforced true if schema is enforced (validation fails if not matched)
 * @param schema the schema expression
 * @param location metadata location
 */
public record SchemaStatement(
        @NonNull SubscriptionElement element,
        boolean enforced,
        @NonNull Expression schema,
        @NonNull SourceLocation location) implements AstNode {}
