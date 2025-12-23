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
package io.sapl.spring.data.mongo.queries;

import reactor.util.annotation.Nullable;

public record SaplCondition(String field, Object value, OperatorMongoDB operator, @Nullable String conjunction) {
    public SaplCondition(String field, Object value, OperatorMongoDB operator, @Nullable String conjunction) {
        this.field       = field;
        this.value       = value;
        this.operator    = operator;
        this.conjunction = conjunction == null || "and".equalsIgnoreCase(conjunction) ? "And" : "Or";
    }
}
