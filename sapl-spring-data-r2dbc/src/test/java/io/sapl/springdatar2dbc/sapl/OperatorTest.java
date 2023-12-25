/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatar2dbc.sapl;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class OperatorTest {

    final Operator operator = Operator.LESS_THAN_EQUAL;

    @Test
    void when_keywordNotExist_then_throwNotImplementedException() {
        Assertions.assertThrows(NotImplementedException.class, () -> Operator.getOperatorByKeyword("notValid"));
    }

    @Test
    void when_keywordExist_then_returnOperation() {
        Operator result = Operator.getOperatorByKeyword(">=");
        Assertions.assertEquals(result, Operator.GREATER_THAN_EQUAL);
    }

    @Test
    void when_valuesOfOperatorIsNoArray_then_returnFalse() {
        Assertions.assertFalse(operator.isArray);
    }

    @Test
    void when_valuesOfOperatorIsArray_then_returnTrue() {
        Assertions.assertTrue(Operator.BETWEEN.isArray);
    }

    @Test
    void when_keywordExists_then_getSqlQueryBasedKeywords() {
        Assertions.assertEquals(operator.sqlQueryBasedKeywords, List.of("<="));
    }

}
