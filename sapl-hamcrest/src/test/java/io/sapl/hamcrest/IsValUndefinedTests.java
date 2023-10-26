/*
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
package io.sapl.hamcrest;

import static io.sapl.hamcrest.Matchers.valUndefined;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class IsValUndefinedTests {

    @Test
    void testTypeFalseError() {
        var sut = valUndefined();
        assertThat(Val.error(), not(is(sut)));
    }

    @Test
    void testTypeFalseValue() {
        var sut = valUndefined();
        assertThat(Val.TRUE, not(is(sut)));
    }

    @Test
    void testType() {
        var sut = valUndefined();
        assertThat(Val.UNDEFINED, is(sut));
    }

    @Test
    void testDescriptionForEmptyConstructor() {
        var                     sut         = valUndefined();
        final StringDescription description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("undefined"));
    }

}
