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
package io.sapl.functions;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class LoggingFunctionLibraryTest {

	@Test
	void debugIsInstantiable() {
		assertThat(new LoggingFunctionLibrary(), is(notNullValue()));
	}

	@Test
	void debugIsIdentity() {
		assertThat(LoggingFunctionLibrary.debug(Val.of("message"), Val.TRUE), is(Val.TRUE));
	}

	@Test
	void infoIsIdentity() {
		assertThat(LoggingFunctionLibrary.info(Val.of("message"), Val.TRUE), is(Val.TRUE));
	}

	@Test
	void infoIsIdentity4UndefinedAsWell() {
		assertThat(LoggingFunctionLibrary.info(Val.of("message"), Val.UNDEFINED), is(Val.UNDEFINED));
	}

	@Test
	void warnIsIdentity() {
		assertThat(LoggingFunctionLibrary.warn(Val.of("message"), Val.TRUE), is(Val.TRUE));
	}

	@Test
	void traceIsIdentity() {
		assertThat(LoggingFunctionLibrary.trace(Val.of("message"), Val.TRUE), is(Val.TRUE));
	}

	@Test
	void errorIsIdentity() {
		assertThat(LoggingFunctionLibrary.error(Val.of("message"), Val.TRUE), is(Val.TRUE));
	}

}
