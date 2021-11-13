/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.mocking.attribute;

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.parentValue;
import static io.sapl.test.Imports.whenAttributeParams;

import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AttributeMockForParentValueAndArgumentsTest {

	private AttributeMockForParentValueAndArguments mock;

	@BeforeEach
	void setUp() {
		mock = new AttributeMockForParentValueAndArguments("attr.test");
	}

	@Test
	void test() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(1))),
				Val.of(true));
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(2))),
				Val.of(false));

		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(1)));
		arguments.add(Flux.just(Val.of(1), Val.of(2)));

		StepVerifier.create(mock.evaluate(Val.of(true), null, arguments)).expectNext(Val.of(true))
				.expectNext(Val.of(false)).thenCancel().verify();

		mock.assertVerifications();
	}

	@Test
	void test_notMatchingMockForParentValue() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1))),
				Val.of(true));

		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(1)));

		Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> StepVerifier
				.create(mock.evaluate(Val.of(false), null, arguments)).expectNext(Val.of(true)).thenCancel().verify());
	}

	@Test
	void test_noMatchingMockForArguments() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(1))),
				Val.of(true));

		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(99)));
		arguments.add(Flux.just(Val.of(99)));

		StepVerifier.create(mock.evaluate(Val.of(true), null, arguments)).expectError().verify();
	}

	@Test
	void test_argumentCountNotMatching() {
		mock.loadMockForParentValueAndArguments(whenAttributeParams(parentValue(val(true)), arguments(val(1), val(1))),
				Val.of(true));

		List<Flux<Val>> arguments = new LinkedList<>();
		arguments.add(Flux.just(Val.of(1)));

		StepVerifier.create(mock.evaluate(Val.of(true), null, arguments)).expectError().verify();
	}

	@Test
	void test_errorMessage() {
		Assertions.assertThat(mock.getErrorMessageForCurrentMode()).isNotEmpty();
	}

}
