/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.compiler;

/**
 * Classification of compiled expression evaluation behavior.
 * <p>
 * Used to determine optimal compilation strategy: VALUE expressions can be
 * folded at compile time, PURE expressions
 * require evaluation context but produce single values, STREAM expressions
 * require reactive subscription.
 */
enum Nature {
    /** Compile-time constant. Can be evaluated immediately without context. */
    VALUE,
    /** Requires evaluation context but produces a single value synchronously. */
    PURE,
    /** Requires reactive subscription. Produces zero or more values over time. */
    STREAM
}
