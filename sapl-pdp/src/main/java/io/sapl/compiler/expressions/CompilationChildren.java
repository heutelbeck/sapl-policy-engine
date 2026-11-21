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
package io.sapl.compiler.expressions;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableList;

@UtilityClass
class CompilationChildren {

    record Pair<L, R>(L first, R second) {}

    record Three<A, B, C>(A first, B second, C third) {}

    static <L, R> Pair<L, R> pair(Supplier<L> first, Supplier<R> second) {
        final L left;
        try {
            left = first.get();
        } catch (SaplCompilerException firstFailure) {
            throw withSiblingFailure(firstFailure, second);
        }
        return new Pair<>(left, second.get());
    }

    private static SaplCompilerException withSiblingFailure(SaplCompilerException first, Supplier<?> sibling) {
        try {
            sibling.get();
            return first;
        } catch (SaplCompilerException second) {
            return SaplCompilerException.multiple(List.of(first, second));
        }
    }

    static <A, B, C> Three<A, B, C> three(Supplier<A> first, Supplier<B> second, Supplier<C> third) {
        val inputs = pair(() -> pair(first, second), third);
        return new Three<>(inputs.first().first(), inputs.first().second(), inputs.second());
    }

    static <S, T> List<T> map(List<S> sources, Function<S, T> compile) {
        val results = new ArrayList<T>(sources.size());
        forEach(sources, source -> results.add(compile.apply(source)));
        return unmodifiableList(results);
    }

    static <S> void forEach(List<S> sources, Consumer<S> compile) {
        val failures = new ArrayList<SaplCompilerException>();
        for (val source : sources) {
            try {
                compile.accept(source);
            } catch (SaplCompilerException failure) {
                failures.add(failure);
            }
        }
        if (failures.size() == 1) {
            throw failures.getFirst();
        }
        if (!failures.isEmpty()) {
            throw SaplCompilerException.multiple(failures);
        }
    }
}
