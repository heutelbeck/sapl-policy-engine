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
package io.sapl.functions.libraries;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ObjectFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(ObjectFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @Test
    void whenKeysOnObject_thenReturnsAllKeys() {
        val object = ObjectValue.builder().put("cultist", Value.of("Wilbur Whateley")).put("role", Value.of("ACOLYTE"))
                .put("sanity", Value.of(42)).build();

        val result = ObjectFunctionLibrary.keys(object);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val keys = (ArrayValue) result;
        assertThat(keys).hasSize(3).contains(Value.of("cultist")).contains(Value.of("role"))
                .contains(Value.of("sanity"));
    }

    @Test
    void whenKeysOnEmptyObject_thenReturnsEmptyArray() {
        val result = ObjectFunctionLibrary.keys(Value.EMPTY_OBJECT);

        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void whenValuesOnObject_thenReturnsAllValues() {
        val object = ObjectValue.builder().put("entity", Value.of("Azathoth")).put("title", Value.of("Daemon Sultan"))
                .put("threatLevel", Value.of(9)).build();

        val result = ObjectFunctionLibrary.values(object);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val values = (ArrayValue) result;
        assertThat(values).hasSize(3).contains(Value.of("Azathoth")).contains(Value.of("Daemon Sultan"))
                .contains(Value.of(9));
    }

    @Test
    void whenValuesOnEmptyObject_thenReturnsEmptyArray() {
        val result = ObjectFunctionLibrary.values(Value.EMPTY_OBJECT);

        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void whenValuesContainsNestedObjects_thenReturnsNestedValues() {
        val nested = ObjectValue.builder().put("site", Value.of("R'lyeh")).build();
        val object = ObjectValue.builder().put("location", nested).put("name", Value.of("Cthulhu")).build();

        val result = ObjectFunctionLibrary.values(object);

        assertThat(result).isInstanceOf(ArrayValue.class);
        val values = (ArrayValue) result;
        assertThat(values).hasSize(2).contains(nested).contains(Value.of("Cthulhu"));
    }

    @Test
    void whenSizeOnObject_thenReturnsPropertyCount() {
        val object = ObjectValue.builder().put("name", Value.of("Carter")).put("sanity", Value.of(77))
                .put("artifacts", Value.of(3)).build();

        val result = ObjectFunctionLibrary.size(object);

        assertThat(result).isEqualTo(Value.of(3));
    }

    @Test
    void whenSizeOnEmptyObject_thenReturnsZero() {
        val result = ObjectFunctionLibrary.size(Value.EMPTY_OBJECT);

        assertThat(result).isEqualTo(Value.of(0));
    }

    @Test
    void whenSizeOnSinglePropertyObject_thenReturnsOne() {
        val object = ObjectValue.builder().put("sealed", Value.TRUE).build();

        val result = ObjectFunctionLibrary.size(object);

        assertThat(result).isEqualTo(Value.of(1));
    }

    @Test
    void whenHasKeyWithExistingKey_thenReturnsTrue() {
        val object = ObjectValue.builder().put("name", Value.of("Pickman")).put("role", Value.of("Artist")).build();

        val result = ObjectFunctionLibrary.hasKey(object, Value.of("name"));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void whenHasKeyWithMissingKey_thenReturnsFalse() {
        val object = ObjectValue.builder().put("name", Value.of("Pickman")).build();

        val result = ObjectFunctionLibrary.hasKey(object, Value.of("email"));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void whenHasKeyWithNullValue_thenReturnsTrue() {
        val object = ObjectValue.builder().put("value", Value.NULL).build();

        val result = ObjectFunctionLibrary.hasKey(object, Value.of("value"));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void whenHasKeyOnEmptyObject_thenReturnsFalse() {
        val result = ObjectFunctionLibrary.hasKey(Value.EMPTY_OBJECT, Value.of("anyKey"));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void whenIsEmptyOnEmptyObject_thenReturnsTrue() {
        val result = ObjectFunctionLibrary.isEmpty(Value.EMPTY_OBJECT);

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void whenIsEmptyOnNonEmptyObject_thenReturnsFalse() {
        val object = ObjectValue.builder().put("key", Value.of("value")).build();

        val result = ObjectFunctionLibrary.isEmpty(object);

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void whenIsEmptyOnMultiPropertyObject_thenReturnsFalse() {
        val object = ObjectValue.builder().put("name", Value.of("Herbert West"))
                .put("profession", Value.of("Reanimator")).put("sanity", Value.of(35)).build();

        val result = ObjectFunctionLibrary.isEmpty(object);

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void whenKeysAndValuesCorrespond_thenOrderMatches() {
        val object = ObjectValue.builder().put("alpha", Value.of(1)).put("beta", Value.of(2)).put("gamma", Value.of(3))
                .build();

        val keys   = (ArrayValue) ObjectFunctionLibrary.keys(object);
        val values = (ArrayValue) ObjectFunctionLibrary.values(object);

        assertThat(keys).hasSameSizeAs(values);
        for (int i = 0; i < keys.size(); i++) {
            val key         = ((TextValue) keys.get(i)).value();
            val valueFromFn = values.get(i);
            assertThat(object.get(key)).isEqualTo(valueFromFn);
        }
    }
}
