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
package io.sapl.spring.constraints.providers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ContentFilterTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        String  a = "";
        Integer b = 0;
    }

    @Test
    void test() throws JacksonException {
        final var constraint = MAPPER.readTree("""
                {
                	"conditions" : [
                		{
                			"path" : "$.a",
                			"type" : "=~",
                			"value" : "^.BC$"
                		}
                	]
                }
                """);
        final var condition  = ContentFilter.predicateFromConditions(constraint, MAPPER);
        final var data       = new DataPoint("ABC", 100);
        assertThat(condition.test(data)).isTrue();
    }
}
