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
package io.sapl.api.pdp;

/**
 * Strategy for selecting the policy index implementation.
 */
public enum IndexingStrategy {

    /** Automatically select based on heuristics (e.g., policy count). */
    AUTO,

    /** Linear scan over all documents. No indexing overhead. */
    NAIVE,

    /** Count-and-eliminate algorithm from the SACMAT '21 paper. */
    CANONICAL,

    /** Multi-Valued Decision Diagram: ternary decision DAG traversal. */
    MDD

}
