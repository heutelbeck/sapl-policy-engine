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
package io.sapl.api.model.jackson;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PDPConfigurationSerializerTests {

    private static JsonMapper mapper;

    @BeforeAll
    static void setup() {
        mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
    }

    @Test
    void when_serializingConfigurationWithCompilerOptions_then_emittedUnderDocumentedKey() throws JacksonException {
        val algorithm     = new CombiningAlgorithm(VotingMode.PRIORITY_DENY, DefaultDecision.DENY,
                ErrorHandling.ABSTAIN);
        val configuration = new PDPConfiguration("arkham-pdp", "v1.0", algorithm,
                Value.ofObject(Map.of("indexing", Value.of("AUTO"))), List.of(),
                new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        val json          = mapper.writeValueAsString(configuration);

        // Contract: the documented key is "compilerOptions", not "compilerFlags".
        assertThat(json).contains("\"compilerOptions\":{\"indexing\":\"AUTO\"}").doesNotContain("compilerFlags");
    }
}
