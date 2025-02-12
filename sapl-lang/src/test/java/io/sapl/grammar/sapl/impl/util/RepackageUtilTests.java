/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import reactor.util.function.Tuples;

class RepackageUtilTests {

    @Test
    void testObjectCombiningOnErrors1() {
        final var t1     = Tuples.of("key1", Val.of("value1"));
        final var t2     = Tuples.of("key2", ErrorFactory.error("error1"));
        final var t3     = Tuples.of("key3", Val.of("value3"));
        final var t4     = Tuples.of("key4", Val.of("value4"));
        final var actual = RepackageUtil.recombineObject(new Object[] { t1, t2, t3, t4 });
        assertThat(actual).isEqualTo(ErrorFactory.error("error1"));
    }

    @Test
    void testObjectCombiningOnErrors2() {
        final var t1     = Tuples.of("key1", Val.of("value1"));
        final var t2     = Tuples.of("key2", ErrorFactory.error("error1"));
        final var t3     = Tuples.of("key3", ErrorFactory.error("error2"));
        final var t4     = Tuples.of("key4", Val.of("value4"));
        final var actual = RepackageUtil.recombineObject(new Object[] { t1, t2, t3, t4 });
        assertThat(actual).isEqualTo(ErrorFactory.error("error1"));
    }
}
