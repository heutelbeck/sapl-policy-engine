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
 * <p>
 * Each record captures the complete context of an attribute lookup: what
 * attribute was called, with what arguments, what value was returned, and where
 * in the policy source the invocation occurred. This enables reconstruction of
 * the exact data used for any authorization decision.
 *
 * <p>
 * Attribute records are captured per call-site, without deduplication. If the
 * same attribute is called at two different locations in a policy, two separate
 * records are created. This provides better debugging context than
 * deduplicating
 * by attribute name.
 *
 * <p>
 * This is an internal API for use by PDP implementations and trusted tooling
 * (such as the SAPL Playground). External consumers should use
 * {@link io.sapl.api.pdp.AuthorizationDecision} which does not expose trace
 * information.
 *
 * @param attributeName the fully qualified name of the attribute (e.g.,
 * "user.role" or "time.now")
 * @param entity the entity value passed to the attribute finder (the
 * left-hand side of the {@code <>} operator), or
 * {@link Value#UNDEFINED} for environment attributes
 * @param arguments the argument values passed to the attribute finder
 * @param value the value returned by the attribute finder
 * @param retrievedAt the timestamp when the attribute value was retrieved
 * @param location the source location of the attribute invocation in the
 * policy (may be null for programmatic invocations)
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
