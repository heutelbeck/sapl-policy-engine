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
package io.sapl.attributes.store;

import io.sapl.api.documentation.LibraryDocumentation;

/**
 * Handle returned by {@link InMemoryAttributeStore#load(Object)}
 * that identifies a loaded Policy Information Point. The plugin
 * engine holds the handle and uses it to unload the PIP or swap it
 * for another instance via
 * {@link InMemoryAttributeStore#swap(PipHandle, Object)}.
 */
public interface PipHandle extends AutoCloseable {

    /**
     * @return the namespace declared by the PIP's
     * {@link io.sapl.api.attributes.PolicyInformationPoint} annotation
     */
    String pipName();

    /**
     * @return {@code true} until {@link #unload()} or a successful
     * {@link InMemoryAttributeStore#swap(PipHandle, Object)} marks
     * this handle inactive
     */
    boolean isLoaded();

    /**
     * @return the {@link LibraryDocumentation} extracted from the
     * loaded PIP class, suitable for IDE hover, autocompletion, and
     * offline documentation generation
     */
    LibraryDocumentation documentation();

    /**
     * Removes this PIP from the store's catalog. Idempotent.
     * Active backing subscriptions served by this PIP receive an
     * {@link io.sapl.api.model.ErrorValue} and are torn down.
     */
    void unload();

    @Override
    default void close() {
        unload();
    }
}
