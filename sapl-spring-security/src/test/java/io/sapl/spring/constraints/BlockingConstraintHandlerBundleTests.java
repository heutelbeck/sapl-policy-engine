/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.constraints;

import static com.spotify.hamcrest.optional.OptionalMatchers.emptyOptional;
import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class BlockingConstraintHandlerBundleTests {

    @Test
    void when_filterOptionalFalse_then_returnsEmpty() {
        var filter = (Predicate<Object>) o -> false;
        // @formatter:off
		var sut    =  BlockingConstraintHandlerBundle.<Optional<String>>preEnforceConstraintHandlerBundle(
							FunctionUtil.noop(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							filter,FunctionUtil.sink(),
							UnaryOperator.identity());
		// @formatter:on
        var result = (Optional<String>) sut.handleAllOnNextConstraints(Optional.of("Hello"));
        assertThat(result, is(emptyOptional()));
    }

    @Test
    void when_filterOptionalTrue_then_returnsOriginal() {
        var filter = (Predicate<Object>) o -> true;
        // @formatter:off
		var sut    =  BlockingConstraintHandlerBundle.<Optional<String>>preEnforceConstraintHandlerBundle(
							FunctionUtil.noop(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							filter,FunctionUtil.sink(),
							UnaryOperator.identity());
		// @formatter:on
        var result = (Optional<String>) sut.handleAllOnNextConstraints(Optional.of("Hello"));
        assertThat(result, is(optionalWithValue(is("Hello"))));

    }

    @Test
    void when_filterList_then_elementsRemoved() {
        var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
        // @formatter:off
		var sut    =  BlockingConstraintHandlerBundle.<List<String>>preEnforceConstraintHandlerBundle(
							FunctionUtil.noop(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							filter,FunctionUtil.sink(),
							UnaryOperator.identity());
		// @formatter:on
        var result = (List<String>) sut.handleAllOnNextConstraints(List.of("Alice", "Bob", "Ada", "Adam", "Donald"));
        assertThat(result, contains("Alice", "Ada", "Adam"));
    }

    @Test
    void when_filterArray_then_elementsRemoved() {
        var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
        // @formatter:off
		var sut    =  BlockingConstraintHandlerBundle.<String[]>preEnforceConstraintHandlerBundle(
							FunctionUtil.noop(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							filter,FunctionUtil.sink(),
							UnaryOperator.identity());
		// @formatter:on
        var result = (String[]) sut
                .handleAllOnNextConstraints(new String[] { "Alice", "Bob", "Ada", "Adam", "Donald" });
        assertThat(result, arrayContaining("Alice", "Ada", "Adam"));
    }

    @Test
    void when_filterSet_then_elementsRemoved() {
        var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
        // @formatter:off
		var sut    =  BlockingConstraintHandlerBundle.<Set<String>>preEnforceConstraintHandlerBundle(
							FunctionUtil.noop(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							filter,FunctionUtil.sink(),
							UnaryOperator.identity());
		// @formatter:on
        var result = (Set<String>) sut.handleAllOnNextConstraints(Set.of("Alice", "Bob", "Ada", "Adam", "Donald"));
        assertThat(result, containsInAnyOrder("Alice", "Ada", "Adam"));
    }

    @Test
    void when_filterNonContainerType_then_null() {
        var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
        // @formatter:off
		var sut    =  BlockingConstraintHandlerBundle.<String>preEnforceConstraintHandlerBundle(
							FunctionUtil.noop(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							filter,FunctionUtil.sink(),
							UnaryOperator.identity());
		// @formatter:on
        var result = sut.handleAllOnNextConstraints("Bob");
        assertThat(result, is(nullValue()));
    }

    @Test
    void when_notFilteredNonContainerType_then_original() {
        var filter = (Predicate<Object>) o -> ((String) o).startsWith("A");
        // @formatter:off
		var sut    =  BlockingConstraintHandlerBundle.<String>preEnforceConstraintHandlerBundle(
							FunctionUtil.noop(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							FunctionUtil.sink(),
							UnaryOperator.identity(),
							filter,FunctionUtil.sink(),
							UnaryOperator.identity());
		// @formatter:on
        var result = sut.handleAllOnNextConstraints("Alice");
        assertThat(result, is("Alice"));
    }

}
