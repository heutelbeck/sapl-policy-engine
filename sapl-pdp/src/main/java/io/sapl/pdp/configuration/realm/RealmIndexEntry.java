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
package io.sapl.pdp.configuration.realm;

/**
 * One entry of a {@link RealmIndex}: a bundle that belongs to the realm.
 *
 * @param pdpId the stable identity of the bundle within the realm; the key a
 * consumer loads and removes under
 * @param configId the current version identifier; equals the bundle's own
 * {@code configurationId}. A change signals the consumer to refetch.
 * @param url the absolute URL of the immutable bundle for this {@code configId}
 */
public record RealmIndexEntry(String pdpId, String configId, String url) {}
