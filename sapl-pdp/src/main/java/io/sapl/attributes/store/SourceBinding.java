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

import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;

/**
 * Result of {@link Source#open}. Pairs the value stream with an
 * opaque tag the store may use to identify the binding's
 * provenance (e.g. for matching against a catalog-change event so
 * the store can rebind or evict the right backings during hot-swap).
 *
 * @param stream the value stream the source produced
 * @param tag opaque identifier the store may compare to itself;
 * a catalog-backed source uses the resolved
 * {@link StreamAttributeFinderSpecification}; sources whose
 * provenance is irrelevant to the store may pass {@code null}
 */
record SourceBinding(Stream<Value> stream, Object tag) {}
