/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributes.broker.pip;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import lombok.val;

@DisplayName("StreamAttributeMethodSignatureProcessor")
class StreamAttributeMethodSignatureProcessorTests {

    static final class NullReturningPip {

        @EnvironmentAttribute(name = "nullStream")
        public static Stream<Value> nullStream() {
            return null;
        }
    }

    private static AttributeFinderInvocation invocationFor(String attributeName) {
        val ctx = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        return new AttributeFinderInvocation("config", attributeName, List.of(), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 3, false, ctx);
    }

    @Test
    @DisplayName("when a stream-returning PIP body returns Java null then the finder emits an immediate returned-null error")
    void whenStreamReturningPipReturnsNullThenFinderEmitsReturnedNullError() throws Exception {
        final Method method = NullReturningPip.class.getMethod("nullStream");

        val spec   = StreamAttributeMethodSignatureProcessor.processAttributeMethod(null, "pip", method);
        val stream = spec.attributeFinder().invoke(invocationFor("pip.nullStream"));

        assertThat(stream).isNotNull();
        final Value emitted = stream.awaitNext();
        assertThat(emitted).isInstanceOfSatisfying(ErrorValue.class, error -> assertThat(error.message()).isEqualTo(
                StreamAttributeMethodSignatureProcessor.ERROR_ATTRIBUTE_RETURNED_NULL.formatted("pip.nullStream")));
    }
}
