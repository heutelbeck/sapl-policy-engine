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
package io.sapl.assertj;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.SaplError;

class ValAssertTests {

    @Test
    void isErrorPositive() {
        final var sut = assertThatVal(Val.error(SaplError.UNKNOWN_ERROR));
        assertDoesNotThrow((Executable) sut::isError);
    }

    @Test
    void isErrorNegative() {
        final var sut = assertThatVal(Val.TRUE);
        assertThatThrownBy(sut::isError).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be an <ERROR[]> with any message but was");
    }

    @Test
    void isErrorWithMessagePositive() {
        final var sut = assertThatVal(Val.error(SaplError.UNKNOWN_ERROR));
        assertDoesNotThrow(() -> sut.isError(SaplError.UNKNOWN_ERROR_MESSAGE));
    }

    @Test
    void isErrorWithMessageNegativeWrongMessage() {
        final var sut = assertThatVal(Val.error(SaplError.UNKNOWN_ERROR));
        assertThatThrownBy(() -> sut.isError("another message")).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <ERROR[another message]>");
    }

    @Test
    void isErrorWithMessageNegativeNotAnError() {
        final var sut = assertThatVal(Val.TRUE);
        assertThatThrownBy(() -> sut.isError("message")).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <ERROR[message]>");
    }

    @Test
    void noErrorPositive() {
        final var sut = assertThatVal(Val.TRUE);
        assertDoesNotThrow(sut::noError);
    }

    @Test
    void noErrorNegative() {
        final var sut = assertThatVal(Val.error((String) null));
        assertThatThrownBy(sut::noError).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to not be an error but was");
    }

    @Test
    void isUndefinedPositive() {
        final var sut = assertThatVal(Val.UNDEFINED);
        assertDoesNotThrow(sut::isUndefined);
    }

    @Test
    void isUndefinedNegative() {
        final var sut = assertThatVal(Val.TRUE);
        assertThatThrownBy(sut::isUndefined).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <undefined> but was");
    }

    @Test
    void isSecretPositive() {
        final var sut = assertThatVal(Val.TRUE.asSecret());
        assertDoesNotThrow(sut::isSecret);
    }

    @Test
    void isSecretNegative() {
        final var sut = assertThatVal(Val.TRUE);
        assertThatThrownBy(sut::isSecret).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <SECRET> but was");
    }

    @Test
    void hasValuePositive() {
        final var sut = assertThatVal(Val.TRUE);
        assertDoesNotThrow(sut::hasValue);
    }

    @Test
    void hasValueNegativeUndefined() {
        final var sut = assertThatVal(Val.UNDEFINED);
        assertThatThrownBy(sut::hasValue).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Excpected Val to be defined");
    }

    @Test
    void hasValueNegativeError() {
        final var sut = assertThatVal(Val.error(SaplError.UNKNOWN_ERROR));
        assertThatThrownBy(sut::hasValue).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Excpected Val to be defined");
    }

    @Test
    void hasValueAssertionChaining() throws JsonProcessingException {
        final var sut = assertThatVal(Val.ofJson("{\"key\":\"value\" }"));
        assertDoesNotThrow(() -> sut.hasValue().isObject().containsKey("key"));
    }

    @Test
    void isTruePositive() {
        final var sut = assertThatVal(Val.TRUE);
        assertDoesNotThrow(sut::isTrue);
    }

    @Test
    void isTrueNegativeIsFalse() {
        final var sut = assertThatVal(Val.FALSE);
        assertThatThrownBy(sut::isTrue).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <true>");
    }

    @Test
    void isTrueNegativeIsWrongType() {
        final var sut = assertThatVal(Val.of("not Boolean"));
        assertThatThrownBy(sut::isTrue).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <true>");
    }

    @Test
    void isFalsePositive() {
        final var sut = assertThatVal(Val.FALSE);
        assertDoesNotThrow(sut::isFalse);
    }

    @Test
    void isFalseNegativeIsTrue() {
        final var sut = assertThatVal(Val.TRUE);
        assertThatThrownBy(sut::isFalse).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <false>");
    }

    @Test
    void isFalseNegativeIsWrongType() {
        final var sut = assertThatVal(Val.of("not Boolean"));
        assertThatThrownBy(sut::isFalse).isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Val to be <false>");
    }

}
