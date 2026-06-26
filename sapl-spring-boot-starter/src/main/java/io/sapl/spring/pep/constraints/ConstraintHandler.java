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
package io.sapl.spring.pep.constraints;

import java.util.function.UnaryOperator;

public sealed interface ConstraintHandler<T>
        permits ConstraintHandler.Mapper, ConstraintHandler.Consumer, ConstraintHandler.Runner {
    non-sealed interface Mapper<T> extends ConstraintHandler<T>, UnaryOperator<T> {
    }

    non-sealed interface Consumer<T> extends ConstraintHandler<T>, java.util.function.Consumer<T> {
    }

    non-sealed interface Runner extends ConstraintHandler<Void>, Runnable {
    }
}
