/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

class LoggingFunctionLibraryTests {

    @Test
    void isInstantiable() {
        assertThat(new LoggingFunctionLibrary(), is(notNullValue()));
    }

    @Test
    void debugSpyIsIdentity() {
        assertThat(LoggingFunctionLibrary.debugSpy(Val.of("message"), Val.FALSE), is(Val.FALSE));
    }

    @Test
    void debugSpyOfErrorIsIdentity() {
        assertThat(LoggingFunctionLibrary.debugSpy(Val.of("message"), Val.error()), is(Val.error()));
    }

    @Test
    void infoSpyIsIdentity() {
        assertThat(LoggingFunctionLibrary.infoSpy(Val.of("message"), Val.FALSE), is(Val.FALSE));
    }

    @Test
    void warnSpyIsIdentity() {
        assertThat(LoggingFunctionLibrary.warnSpy(Val.of("message"), Val.FALSE), is(Val.FALSE));
    }

    @Test
    void traceSpyIsIdentity() {
        assertThat(LoggingFunctionLibrary.traceSpy(Val.of("message"), Val.FALSE), is(Val.FALSE));
    }

    @Test
    void errorSpyIsIdentity() {
        assertThat(LoggingFunctionLibrary.errorSpy(Val.of("message"), Val.FALSE), is(Val.FALSE));
    }

    @Test
    void debugIsTrue() {
        assertThat(LoggingFunctionLibrary.debug(Val.of("message"), Val.FALSE), is(Val.TRUE));
    }

    @Test
    void infoIsTrue() {
        assertThat(LoggingFunctionLibrary.info(Val.of("message"), Val.FALSE), is(Val.TRUE));
    }

    @Test
    void infoIsTrueForUndefinedAsWell() {
        assertThat(LoggingFunctionLibrary.info(Val.of("message"), Val.UNDEFINED), is(Val.TRUE));
    }

    @Test
    void warnIsTrue() {
        assertThat(LoggingFunctionLibrary.warn(Val.of("message"), Val.FALSE), is(Val.TRUE));
    }

    @Test
    void traceIsTrue() {
        assertThat(LoggingFunctionLibrary.trace(Val.of("message"), Val.FALSE), is(Val.TRUE));
    }

    @Test
    void errorIsTrue() {
        assertThat(LoggingFunctionLibrary.error(Val.of("message"), Val.FALSE), is(Val.TRUE));
    }

}
