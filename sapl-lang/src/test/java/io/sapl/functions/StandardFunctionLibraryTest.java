/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import static io.sapl.hamcrest.Matchers.val;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class StandardFunctionLibraryTest {

	@Test
	void instantiatable() {
		assertThat(new StandardFunctionLibrary(), notNullValue());
	}

	@Test
	void lengthOfEmptyIsZero() {
		assertThat(StandardFunctionLibrary.length(Val.ofEmptyArray()), is(val(0)));
		assertThat(StandardFunctionLibrary.length(Val.ofEmptyObject()), is(val(0)));
	}

	@Test
	void lengthOfArrayWithElements() {
		var array = Val.JSON.arrayNode();
		array.add(Val.JSON.booleanNode(false));
		array.add(Val.JSON.booleanNode(false));
		array.add(Val.JSON.booleanNode(false));
		array.add(Val.JSON.booleanNode(false));
		assertThat(StandardFunctionLibrary.length(Val.of(array)), is(val(4)));
	}

	@Test
	void lengthOfObjectWithElements() {
		var object = Val.JSON.objectNode();
		object.set("key1", Val.JSON.booleanNode(false));
		object.set("key2", Val.JSON.booleanNode(false));
		object.set("key3", Val.JSON.booleanNode(false));
		object.set("key4", Val.JSON.booleanNode(false));
		object.set("key5", Val.JSON.booleanNode(false));
		assertThat(StandardFunctionLibrary.length(Val.of(object)), is(val(5)));

	}

	@Test
	void lengthOfText() {
		assertThat(StandardFunctionLibrary.length(Val.of("ABC")), is(val(3)));
	}

	@Test
	void numberToStringBooleanLeftIntact() {
		assertThat(StandardFunctionLibrary.numberToString(Val.TRUE), is(val("true")));
	}

	@Test
	void numberToStringSomeNumberLeftIntact() {
		assertThat(StandardFunctionLibrary.numberToString(Val.of(1.23e-1D)), is(val("0.123")));
	}

	@Test
	void numberToStringNullEmptyString() {
		assertThat(StandardFunctionLibrary.numberToString(Val.NULL), is(val("")));
	}

	@Test
	void numberToStringTextIntact() {
		assertThat(StandardFunctionLibrary.numberToString(Val.of("ABC")), is(val("ABC")));
	}

}
