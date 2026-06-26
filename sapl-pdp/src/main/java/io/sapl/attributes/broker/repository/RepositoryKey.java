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
package io.sapl.attributes.broker.repository;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.sapl.api.pdp.StreamingPolicyDecisionPoint.DEFAULT_PDP_ID;
import static io.sapl.api.shared.NameValidator.requireValidName;

/**
 * Writer-side key for an {@link AttributeRepository}. Within a tenant
 * ({@code pdpId}) the triple {@code (entity, name, arguments)} uniquely
 * identifies a published value. A {@code null} {@code entity} denotes a
 * global (environment) attribute. Arguments are defensively copied at
 * construction.
 * <p>
 * The {@code pdpId} partitions values by tenant so a value published for
 * one tenant is never observed by another. Publishers that do not
 * partition by tenant may use the {@code (entity, name, arguments)}
 * constructor, which targets the default tenant.
 * <p>
 * Structurally aligned with {@link AttributeFinderInvocation} so a
 * subscription's invocation can be projected onto a repository key
 * via {@link #fromInvocation(AttributeFinderInvocation)}. Per-invocation
 * timing fields (initialTimeOut, pollInterval, backoff, retries) are
 * not part of the repository key: repository values are slot-based
 * and have no pumping policy.
 *
 * @param entity entity context, or {@code null} for global attributes
 * @param name fully qualified attribute name
 * @param arguments attribute arguments (defensively copied)
 * @param pdpId tenant the value belongs to
 *
 * @since 4.1.0
 */
public record RepositoryKey(
        @Nullable Value entity,
        @NonNull String name,
        @NonNull List<Value> arguments,
        @NonNull String pdpId) {

    public RepositoryKey {
        requireValidName(name);
        arguments = List.copyOf(arguments);
    }

    /**
     * Convenience for single-tenant publishers: targets the default tenant.
     *
     * @param entity entity context, or {@code null} for global attributes
     * @param name fully qualified attribute name
     * @param arguments attribute arguments (defensively copied)
     */
    public RepositoryKey(@Nullable Value entity, @NonNull String name, @NonNull List<Value> arguments) {
        this(entity, name, arguments, DEFAULT_PDP_ID);
    }

    /**
     * Projects an {@link AttributeFinderInvocation} onto a repository
     * key by dropping the per-invocation timing fields, preserving the
     * invocation's tenant ({@code pdpId}).
     *
     * @param invocation the invocation to project
     * @return the corresponding repository key
     */
    public static RepositoryKey fromInvocation(@NonNull AttributeFinderInvocation invocation) {
        return new RepositoryKey(invocation.entity(), invocation.attributeName(), invocation.arguments(),
                invocation.pdpId());
    }
}
