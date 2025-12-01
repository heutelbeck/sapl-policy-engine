/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp.internal;

import io.sapl.api.SaplVersion;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;
import lombok.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Records a single attribute invocation for auditing and debugging.
 *
 * @param attributeName fully qualified attribute name (e.g., "user.role")
 * @param entity left-hand side of {@code <>}, or UNDEFINED for environment
 * attributes
 * @param arguments argument values passed to the attribute finder
 * @param value the returned value
 * @param retrievedAt when the value was retrieved
 * @param location source location of the invocation (may be null)
 */
public record AttributeRecord(
        @NonNull String attributeName,
        @NonNull Value entity,
        @NonNull List<Value> arguments,
        @NonNull Value value,
        @NonNull Instant retrievedAt,
        SourceLocation location) implements Serializable {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
}
