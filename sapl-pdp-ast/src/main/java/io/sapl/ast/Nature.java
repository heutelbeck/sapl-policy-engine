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
package io.sapl.ast;

/**
 * Classification of expression evaluation nature.
 * <p>
 * Determines how an expression can be evaluated:
 * <ul>
 * <li>{@link #VALUE} - Constant, already folded at compile time</li>
 * <li>{@link #PURE_STATIC} - Pure evaluation, no subscription dependency, can
 * be folded if inputs become constant</li>
 * <li>{@link #PURE_DYNAMIC} - Pure evaluation, depends on subscription
 * (subject/action/resource/environment), must evaluate per request</li>
 * <li>{@link #STREAM} - Reactive evaluation, involves attribute finders</li>
 * </ul>
 * <p>
 * Nature propagates through expressions: STREAM > PURE_DYNAMIC > PURE_STATIC >
 * VALUE. If any sub-expression is STREAM, the whole expression is STREAM.
 */
public enum Nature {

    /**
     * Constant value - already evaluated at compile time.
     */
    VALUE,

    /**
     * Pure evaluation, not subscription-dependent. Can be constant folded if all
     * inputs become constants.
     */
    PURE_STATIC,

    /**
     * Pure evaluation, depends on subscription context
     * (subject/action/resource/environment). Must be evaluated for each request.
     */
    PURE_DYNAMIC,

    /**
     * Reactive evaluation - involves attribute finders. Returns a stream of values.
     */
    STREAM;

    /**
     * Combines two natures, returning the more general one. Used when computing the
     * nature of compound expressions.
     *
     * @param other the other nature
     * @return the combined nature (most general of the two)
     */
    public Nature combine(Nature other) {
        // STREAM > PURE_DYNAMIC > PURE_STATIC > VALUE
        return this.ordinal() > other.ordinal() ? this : other;
    }

    /**
     * Combines multiple natures, returning the most general one.
     *
     * @param natures the natures to combine
     * @return the combined nature
     */
    public static Nature combine(Nature... natures) {
        var result = VALUE;
        for (var nature : natures) {
            result = result.combine(nature);
        }
        return result;
    }

}
