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
package io.sapl.compiler.util;

/**
 * Stratum levels for compiled expressions. Used to classify and verify the
 * compile-time vs runtime nature of expressions.
 * <ul>
 * <li>VALUE: Compile-time constant (strata 1)</li>
 * <li>PURE_NON_SUB: Runtime, not depending on subscription (strata 2)</li>
 * <li>PURE_SUB: Runtime, depending on subscription (strata 3)</li>
 * <li>STREAM: Reactive StreamOperator (strata 4)</li>
 * </ul>
 */
public enum Stratum {
    VALUE(1),
    PURE_NON_SUB(2),
    PURE_SUB(3),
    STREAM(4);

    public final int level;

    Stratum(int level) {
        this.level = level;
    }
}
