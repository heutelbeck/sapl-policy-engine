/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.inmemory.indexed;

import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

public class ConjunctiveClauseTest {

    private SaplFactory factory;

    @Before
    public void setUp() {
        factory = new SaplFactoryImpl();
    }

    @Test
    public void testConstructor() {
        // given
        ArrayList<Literal> emptyList = new ArrayList<>();

        // then
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            new ConjunctiveClause(emptyList);
        }).withNoCause();
    }

    @Test
    public void testReductionOfEqualLiterals() {
        // given
        BasicIdentifier id0 = createIdentifier("A");
        BasicIdentifier id1 = createIdentifier("B");
        ConjunctiveClause expanded = new ConjunctiveClause(new Literal(new Bool(id0, Collections.emptyMap())),
                new Literal(new Bool(id0, Collections.emptyMap())), new Literal(new Bool(id1, Collections.emptyMap()), false));
        ConjunctiveClause reference = new ConjunctiveClause(new Literal(new Bool(id0, Collections.emptyMap())),
                new Literal(new Bool(id1, Collections.emptyMap()), false));

        // when
        ConjunctiveClause reduced = expanded.reduce();

        // then
        Assertions.assertThat(reduced).isEqualTo(reference);
    }

    @Test
    public void testSubsetOf() {
        // given
        BasicIdentifier id0 = createIdentifier("A");
        BasicIdentifier id1 = createIdentifier("B");
        BasicIdentifier id2 = createIdentifier("C");
        ConjunctiveClause base = new ConjunctiveClause(new Literal(new Bool(id0, Collections.emptyMap())),
                new Literal(new Bool(id1, Collections.emptyMap())), new Literal(new Bool(id2, Collections.emptyMap())));
        ConjunctiveClause subset = new ConjunctiveClause(new Literal(new Bool(id1, Collections.emptyMap())),
                new Literal(new Bool(id2, Collections.emptyMap())));

        // then
        Assertions.assertThat(subset.isSubsetOf(base)).isTrue();
        Assertions.assertThat(base.isSubsetOf(base)).isTrue();
        Assertions.assertThat(base.isSubsetOf(subset)).isFalse();
    }

    private BasicIdentifier createIdentifier(String identifier) {
        BasicIdentifier result = factory.createBasicIdentifier();
        result.setIdentifier(identifier);
        return result;
    }

}
