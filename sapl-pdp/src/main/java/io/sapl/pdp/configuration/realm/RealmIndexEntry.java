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
 * <p>
 * The URL is the binding the consumer monitors autonomously. A URL pointing at
 * the mutable latest endpoint tracks updates as the server publishes them. A
 * URL pointing at an immutable version pins the pdpId to that exact version,
 * which is how a deliberate rollback is expressed: it arrives as a signed
 * rebinding through the index.
 *
 * @param pdpId the stable identity of the bundle within the realm, the key a
 * consumer loads and removes under
 * @param url the absolute URL of the bundle endpoint to monitor
 */
public record RealmIndexEntry(String pdpId, String url) {}
