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
package io.sapl.test.mocking.function;

import static io.sapl.test.Imports.times;
import static io.sapl.test.Imports.whenFunctionParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.test.SaplTestException;

class MockingFunctionContextTests {

    FunctionContext unmockedCtx;

    MockingFunctionContext ctx;

    @BeforeEach
    void setup() {
        this.unmockedCtx = Mockito.mock(AnnotationFunctionContext.class);
        this.ctx         = new MockingFunctionContext(unmockedCtx);
    }

    @Test
    void templatesEmpty() {
        assertThat(this.ctx.getCodeTemplates()).isEmpty();
        assertThat(this.ctx.getAllFullyQualifiedFunctions()).isEmpty();
    }

    @Test
    void test_invalidFullName() {
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.checkImportName("foo"));
        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> ctx.checkImportName("foo.bar.xxx"));
    }

    @Test
    void test_isProvided_False() {
        assertThat(this.ctx.isProvidedFunction("foo.bar")).isFalse();
    }

    @Test
    void test_FunctionMockAlwaysSameValue_duplicateRegistration() {
        this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", Val.of(1));
        var val1 = Val.of(1);
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", val1));
    }

    @Test
    void test_alreadyDefinedMock_NotFunctionMockSequence() {
        this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", Val.of(1));
        var val1 = Val.of(1);
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> this.ctx.loadFunctionMockReturnsSequence("foo.bar", new Val[] { val1 }));
    }

    @Test
    void test_alreadyDefinedMock_NotFunctionMockAlwaysSameForParameters() {
        this.ctx.loadFunctionMockAlwaysSameValue("foo.bar", Val.of(1));
        var val1   = Val.of(1);
        var params = whenFunctionParams(is(Val.of(1)));
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> this.ctx.loadFunctionMockAlwaysSameValueForParameters("foo.bar", val1, params));
    }

    @Test
    void test_FunctionMockValueFromFunction_duplicateRegistration() {
        this.ctx.loadFunctionMockValueFromFunction("foo.bar", call -> Val.of(1));
        assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> this.ctx.loadFunctionMockValueFromFunction("foo.bar", call -> Val.of(1)));
    }

    @Test
    void test_isProvided() {
        when(unmockedCtx.isProvidedFunction("iii.iii")).thenReturn(Boolean.TRUE);
        when(unmockedCtx.providedFunctionsOfLibrary("foo")).thenReturn(List.of());
        when(unmockedCtx.providedFunctionsOfLibrary("xxx")).thenReturn(List.of());
        when(unmockedCtx.providedFunctionsOfLibrary("iii")).thenReturn(List.of("iii", "iiii"));

        ctx.loadFunctionMockValueFromFunction("foo.bar", call -> Val.of("1"));
        ctx.loadFunctionMockValueFromFunction("foo.abc", call -> Val.of("1"), times(1));
        ctx.loadFunctionMockAlwaysSameValue("xxx.xxx", Val.of(1));
        ctx.loadFunctionMockAlwaysSameValue("xxx.yyy", Val.of(1), times(1));
        ctx.loadFunctionMockAlwaysSameValueForParameters("foo.123", Val.of(1), whenFunctionParams());
        ctx.loadFunctionMockAlwaysSameValueForParameters("foo.456", Val.of(1), whenFunctionParams(), times(1));
        ctx.loadFunctionMockOnceReturnValue("foo.789", Val.of(1));
        ctx.loadFunctionMockReturnsSequence("foo.987", new Val[] { Val.of(1) });

        assertThat(ctx.isProvidedFunction("foo.bar")).isTrue();
        assertThat(ctx.isProvidedFunction("foo.abc")).isTrue();
        assertThat(ctx.isProvidedFunction("xxx.xxx")).isTrue();
        assertThat(ctx.isProvidedFunction("xxx.yyy")).isTrue();
        assertThat(ctx.isProvidedFunction("foo.123")).isTrue();
        assertThat(ctx.isProvidedFunction("foo.456")).isTrue();
        assertThat(ctx.isProvidedFunction("foo.789")).isTrue();
        assertThat(ctx.isProvidedFunction("foo.987")).isTrue();
        assertThat(ctx.isProvidedFunction("iii.iii")).isTrue();

        assertThat(ctx.providedFunctionsOfLibrary("foo"))
                .containsAll(List.of("bar", "abc", "123", "456", "789", "987"));
        assertThat(ctx.providedFunctionsOfLibrary("xxx")).containsAll(List.of("xxx", "yyy"));
        assertThat(ctx.providedFunctionsOfLibrary("iii")).containsAll(List.of("iii", "iiii"));

    }

    @Test
    void test_ReturnUnmockedEvaluation() {
        when(unmockedCtx.evaluate(any(), any(), any(), any(), any())).thenReturn(Val.of("abc"));
        assertThat(this.ctx.evaluate(null, "foo.bar", null, null, null)).isEqualTo(Val.of("abc"));
    }

    @Test
    void test_documentation() {
        this.ctx.addNewLibraryDocumentation("foo.bar");
        var unmockedDoc = new LibraryDocumentation("test", "Test");
        unmockedDoc.getDocumentation().put("upper", "blabla");
        when(this.unmockedCtx.getDocumentation()).thenReturn(List.of(unmockedDoc));

        var result = this.ctx.getDocumentation();

        assertThat(result).hasSize(2);
        var iterator = result.iterator();
        var doc1     = iterator.next();
        assertThat(doc1.getName()).isEqualTo("foo");
        assertThat(doc1.getDocumentation()).containsKey("bar");
        var doc2 = iterator.next();
        assertThat(doc2.getName()).isEqualTo("test");
        assertThat(doc2.getDocumentation()).containsKey("upper");
    }

    @Test
    void test_getAvailableLibraries_returnsAllAvailableLibraries() {
        ctx.loadFunctionMockValueFromFunction("foo.bar", call -> Val.of("1"));
        assertThat(this.ctx.getAvailableLibraries()).containsOnly("foo.bar");
    }

    @Test
    void test_getDocumentedAttributeCodeTemplates_isEmpty() {
        assertThat(this.ctx.getDocumentedCodeTemplates()).isEmpty();
    }

    @Test
    void test_getFunctionSchemas_isEmpty() {
        assertThat(this.ctx.getFunctionSchemas()).isEmpty();
    }

}
