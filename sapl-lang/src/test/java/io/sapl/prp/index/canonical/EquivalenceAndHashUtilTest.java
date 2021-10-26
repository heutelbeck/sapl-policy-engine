/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import static io.sapl.grammar.sapl.impl.util.ParserUtil.entitilement;
import static io.sapl.grammar.sapl.impl.util.ParserUtil.expression;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquivalenceAndHashUtilTest {

    @Test
    void testSemanticHash() throws Exception {
        var imports = Map.of("mock", "io.sapl.mock");

        var expA = expression("io.sapl.mock()");
        var expB = expression("mock()");

        var hashA = EquivalenceAndHashUtil.semanticHash(expA, imports);
        var hashB = EquivalenceAndHashUtil.semanticHash(expB, imports);
        var hashC = EquivalenceAndHashUtil.semanticHash(expB, Collections.emptyMap());

        assertThat(hashA, is(hashB));
        assertThat(hashA, not(is(hashC)));

        var exp1 = expression("exp");
        var exp2 = expression("exp");
        var ent1 = entitilement("permit");
        var ent2 = entitilement("deny");

        var hash1 = EquivalenceAndHashUtil.semanticHash(exp1, Collections.emptyMap());
        var hash2 = EquivalenceAndHashUtil.semanticHash(exp2, Collections.emptyMap());
        var hash3 = EquivalenceAndHashUtil.semanticHash(ent1, Collections.emptyMap());
        var hash4 = EquivalenceAndHashUtil.semanticHash(ent2, Collections.emptyMap());

        assertThat(hash1, is(hash2));
        assertThat(hash1, not(is(hash3)));
        assertThat(hash1, not(is(hash4)));
        assertThat(hash3, not(is(hash4)));
    }

    @Test
    void testAreEquivalent() throws Exception {
        var imports = Map.of("mock", "io.sapl.mock");

        var expA = expression("io.sapl.mock()");
        var expB = expression("mock()");
        assertThat(EquivalenceAndHashUtil.areEquivalent(expA, imports, expB, imports), is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(expB, imports, expB, imports), is(true));

        var exp1 = expression("exp1");
        var exp2 = expression("exp1");
        var exp3 = expression("exp2");
        var ent1 = entitilement("permit");
        var ent2 = entitilement("deny");

        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), exp2, Collections.emptyMap()),
                is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), exp3, Collections.emptyMap()),
                is(false));
        assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, Collections.emptyMap(), ent1, Collections.emptyMap()),
                is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, Collections.emptyMap(), ent2, Collections.emptyMap()),
                is(false));
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), ent1, Collections.emptyMap()),
                is(false));

        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), null, Collections.emptyMap()));
        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(null, Collections.emptyMap(), exp2, Collections.emptyMap()));
        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(exp1, null, exp2, Collections.emptyMap()));
        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(exp1, Collections.emptyMap(), exp2, null));

        assertThrows(NullPointerException.class, () -> new Literal((Bool) null));

        // Map<String, String> imports = Map.of("numbers", "test.numbers");
        //
        // var e4 = expression("numbers.MAX_VALUE");
        // var e5 = expression("test.numbers.MAX_VALUE");
        // assertThat(EquivalenceAndHashUtil.areEquivalent(e4, imports, e5, imports),
        // is(true));
    }

    @Test
    void testFeaturesAreEquivalent() {
        var objectMock = mock(Object.class);
        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(objectMock, Collections.emptyMap(), objectMock, Collections.emptyMap()), is(true));

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(objectMock, Collections.emptyMap(), null, Collections.emptyMap()), is(false));

        var eList1 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList1.size()).thenReturn(1);

        var eList2 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList2.size()).thenReturn(2);

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, Collections.emptyMap(), eList2, Collections.emptyMap()), is(false));

        var iter1 = mock(Iterator.class);
        when(eList1.size()).thenReturn(2);
        when(eList1.iterator()).thenReturn(iter1);
        when(iter1.hasNext()).thenReturn(true, true, false);
        when(iter1.next()).thenReturn(objectMock, objectMock, objectMock);

        var iter2 = mock(Iterator.class);
        when(eList2.size()).thenReturn(2);
        when(eList2.iterator()).thenReturn(iter2);
        when(iter2.hasNext()).thenReturn(true, true, false);
        when(iter2.next()).thenReturn(objectMock, null, objectMock);

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, Collections.emptyMap(), eList2, Collections.emptyMap()), is(false));
    }

}
