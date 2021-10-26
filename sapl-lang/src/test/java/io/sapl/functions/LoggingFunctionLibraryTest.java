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
