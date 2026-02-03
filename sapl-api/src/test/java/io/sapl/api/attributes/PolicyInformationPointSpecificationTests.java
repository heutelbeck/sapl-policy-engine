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
package io.sapl.api.attributes;

import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PolicyInformationPointSpecification record.
 */
class PolicyInformationPointSpecificationTests {

    private static final AttributeFinder DUMMY_FINDER = inv -> null;

    @Test
    void when_construction_then_storesNameAndFinders() {
        var finder1 = new AttributeFinderSpecification("test", "attr1", false, List.of(), null, DUMMY_FINDER);
        var finder2 = new AttributeFinderSpecification("test", "attr2", false,
                List.<Class<? extends Value>>of(TextValue.class), null, DUMMY_FINDER);

        var spec = new PolicyInformationPointSpecification("test", Set.of(finder1, finder2));

        assertThat(spec.name()).isEqualTo("test");
        assertThat(spec.attributeFinders()).containsExactlyInAnyOrder(finder1, finder2);
    }

    @Test
    void when_construction_withEmptyFinders_then_emptySet() {
        var spec = new PolicyInformationPointSpecification("test", Set.of());

        assertThat(spec.name()).isEqualTo("test");
        assertThat(spec.attributeFinders()).isEmpty();
    }

    @Test
    void when_equals_sameNameAndFinders_then_areEqual() {
        var finder = new AttributeFinderSpecification("test", "attr", false, List.of(), null, DUMMY_FINDER);

        var spec1 = new PolicyInformationPointSpecification("test", Set.of(finder));
        var spec2 = new PolicyInformationPointSpecification("test", Set.of(finder));

        assertThat(spec1).isEqualTo(spec2).hasSameHashCodeAs(spec2);
    }

    @Test
    void when_equals_differentNames_then_notEqual() {
        var finder = new AttributeFinderSpecification("test", "attr", false, List.of(), null, DUMMY_FINDER);

        var spec1 = new PolicyInformationPointSpecification("test1", Set.of(finder));
        var spec2 = new PolicyInformationPointSpecification("test2", Set.of(finder));

        assertThat(spec1).isNotEqualTo(spec2);
    }
}
