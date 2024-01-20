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
package io.sapl.springdatamongoreactive.sapl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

class OperatorTests {

    final Operator operator = Operator.LESS_THAN_EQUAL;

    @Test
    void when_keywordNotExist_then_throwNotImplementedException() {
        assertThrows(NotImplementedException.class, () -> Operator.getOperatorByKeyword("notValid"));
    }

    @Test
    void when_keywordExist_then_returnOperation() {
        Operator result = Operator.getOperatorByKeyword("$lte");
        assertEquals(Operator.LESS_THAN_EQUAL, result);
    }

    @Test
    void when_keywordExists_then_getSqlQueryBasedKeywords() {
        assertEquals(operator.mongoBasedKeywords, List.of("$lte", "lte"));
    }

    @Test
    void when_keywordExists_then_getMethodNameBasedKeywords() {
        assertEquals(operator.methodNameBasedKeywords, List.of("IsLessThanEqual", "LessThanEqual"));
    }

}
