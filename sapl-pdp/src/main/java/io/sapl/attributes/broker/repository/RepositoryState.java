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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import lombok.NonNull;

/**
 * Complete immutable view of one tenant in one routed source. When available,
 * {@code bindings} contains every current binding at one committed source
 * point. Missing keys are therefore confirmed unbound. A partial map of changed
 * or currently requested keys cannot represent this view: a later-discovered
 * attribute must be readable at the same source state as earlier dependencies.
 * <p>
 * {@code revision} orders complete replacement views within one observation.
 * A newer view subsumes all earlier changes, including changes to keys not
 * touched by the most recent publication. Revisions need not be consecutive or
 * comparable across registrations. They do not represent the observation time
 * or require a particular backend transaction identifier. Unchanged A and newly
 * published B can share a view without having matching binding generations.
 * {@link Long#MIN_VALUE} is reserved for the single-key projection's initial
 * "no accepted view" marker and must not be used as a source revision.
 * <p>
 * If {@code unavailable} is non-null, the source cannot establish its current
 * bindings. Both lookup methods expose that error as the value for every key,
 * including keys missing from the map. Only an available view can establish
 * absence as {@link Value#UNDEFINED}; source failure must not masquerade as
 * unbound data. The map is defensively copied. The contract requires an
 * immutable complete view, without prescribing how the source stores or
 * assembles its data.
 * <p>
 * This is the source's complete view, even when a broker callback contains only
 * the currently available registered keys. A pending external attribute
 * does not permit mixing bindings from different revisions of this view.
 *
 * @param revision order of this view within its observation, greater than
 * {@link Long#MIN_VALUE}
 * @param bindings all current tenant bindings when the source is available
 * @param unavailable source failure, or null when bindings are trustworthy
 */
public record RepositoryState(
        long revision,
        @NonNull Map<RepositoryKey, Binding> bindings,
        @Nullable ErrorValue unavailable) {

    private static final Binding ABSENT = new Binding(-1L, Value.UNDEFINED);

    /**
     * Captures the bindings in an immutable map so later caller updates cannot
     * change an already published view.
     *
     * @param revision order of this view, greater than {@link Long#MIN_VALUE}
     * @param bindings complete bindings when available
     * @param unavailable source failure, or null when available
     * @throws NullPointerException if bindings or any map key or value is null
     */
    public RepositoryState {
        bindings = Map.copyOf(bindings);
    }

    /**
     * One published binding. Its generation stays unchanged through unrelated
     * source changes and replay of that same binding. Rebinding creates a new
     * generation even when the value is equal, so a renewal remains observable.
     * Conversely, unrelated changes must not make an unchanged attribute appear
     * freshly published. The generation identifies the binding; it is neither
     * an observation timestamp nor an expiry deadline.
     *
     * @param generation identity of this binding's publication within the observation
     * @param value immutable value visible to the policy, including SAPL null
     */
    public record Binding(long generation, @NonNull Value value) {}

    /**
     * Looks up a binding from this view. A missing key in an available source
     * returns an absent binding whose value is {@link Value#UNDEFINED}. An
     * unavailable source returns a binding whose value is the source error,
     * regardless of map contents. These synthetic bindings do not identify a
     * published binding lifetime.
     *
     * @param key repository key in this observation
     * @return the binding, with UNDEFINED for confirmed absence or the source
     * error when unavailable
     */
    public Binding binding(RepositoryKey key) {
        if (unavailable != null) {
            return new Binding(revision, unavailable);
        }
        return bindings.getOrDefault(key, ABSENT);
    }

    /** The kind of source fact is independent of the signed binding generation. */
    public enum Presence {
        /** A stored binding, including an explicit UNDEFINED. */
        BOUND,
        /** Confirmed absence in a complete available view. */
        UNBOUND,
        /** The source cannot establish a view. */
        UNAVAILABLE
    }

    /**
     * Identifies the source fact behind a projected value.
     *
     * @param key key in this view
     * @return whether the key is bound, unbound or its source unavailable
     */
    public Presence presence(RepositoryKey key) {
        if (unavailable != null) {
            return Presence.UNAVAILABLE;
        }
        return bindings.containsKey(key) ? Presence.BOUND : Presence.UNBOUND;
    }

    /**
     * Returns the policy-visible value using the same availability and absence
     * rules as {@link #binding(RepositoryKey)}.
     *
     * @param key repository key in this observation
     * @return the visible SAPL value
     */
    public Value value(RepositoryKey key) {
        return binding(key).value();
    }
}
