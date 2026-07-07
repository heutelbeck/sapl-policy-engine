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
 * Stores, per realm, the highest realm index sequence a consumer has accepted.
 * <p>
 * The sequence is the anti-rollback baseline: {@link RealmIndexVerifier} rejects
 * any index whose sequence is not strictly greater than the last accepted one.
 * A store that outlives the process (for example file or database backed)
 * preserves that baseline across restarts, closing the window in which a
 * replayed older but validly signed index would be accepted after a fresh start.
 * The default {@link InMemoryRealmSequenceStore} keeps the baseline only in
 * memory, so it resets on restart and imposes no external storage requirement.
 */
public interface RealmSequenceStore {

    /**
     * Returns the highest sequence accepted for the realm, or {@code -1} when
     * none has been recorded yet, in which case any non-negative sequence is
     * accepted on first contact.
     *
     * @param realm the realm identifier
     * @return the last accepted sequence, or {@code -1} if none has been recorded
     */
    long lastAcceptedSequence(String realm);

    /**
     * Records that an index with the given sequence was accepted for the realm.
     * Called only with a sequence strictly greater than the current value.
     *
     * @param realm the realm identifier
     * @param sequence the accepted sequence
     */
    void recordAcceptedSequence(String realm, long sequence);
}
