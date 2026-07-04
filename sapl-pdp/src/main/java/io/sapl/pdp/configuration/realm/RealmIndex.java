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

import java.util.List;

/**
 * The signed membership of a realm: the set of bundles a consumer should track,
 * plus a monotonic sequence that defeats rollback/replay. Serialized as the
 * payload of a compact JWS by {@link RealmIndexSigner} and verified by
 * {@link RealmIndexVerifier}.
 *
 * @param realm the realm identifier; a consumer refuses an index whose realm
 * does not match the one it expects
 * @param sequence a monotonic counter; a consumer refuses an index whose
 * sequence is not strictly greater than the last it accepted
 * @param issuedAt an informational RFC-3339 timestamp of when the index was
 * produced
 * @param bundles the realm's bundles
 */
public record RealmIndex(String realm, long sequence, String issuedAt, List<RealmIndexEntry> bundles) {

    public RealmIndex {
        bundles = bundles == null ? List.of() : List.copyOf(bundles);
    }
}
