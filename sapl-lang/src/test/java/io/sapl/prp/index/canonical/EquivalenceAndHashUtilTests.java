/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.testutil.ParserUtil.entitlement;
import static io.sapl.testutil.ParserUtil.expression;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.Test;

class EquivalenceAndHashUtilTests {
    private final static Map<String, String> EMPTY_MAP = Map.of();

    @Test
    void testSemanticHash() throws Exception {
        var imports = Map.of("mock", "io.sapl.mock");

        var expA = expression("io.sapl.mock()");
        var expB = expression("mock()");

        var hashA = EquivalenceAndHashUtil.semanticHash(expA, imports);
        var hashB = EquivalenceAndHashUtil.semanticHash(expB, imports);
        var hashC = EquivalenceAndHashUtil.semanticHash(expB, EMPTY_MAP);

        assertThat(hashA, is(hashB));
        assertThat(hashA, not(is(hashC)));

        var exp1 = expression("exp");
        var exp2 = expression("exp");
        var ent1 = entitlement("permit");
        var ent2 = entitlement("deny");

        var hash1 = EquivalenceAndHashUtil.semanticHash(exp1, EMPTY_MAP);
        var hash2 = EquivalenceAndHashUtil.semanticHash(exp2, EMPTY_MAP);
        var hash3 = EquivalenceAndHashUtil.semanticHash(ent1, EMPTY_MAP);
        var hash4 = EquivalenceAndHashUtil.semanticHash(ent2, EMPTY_MAP);

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
        var ent1 = entitlement("permit");
        var ent2 = entitlement("deny");

        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, EMPTY_MAP, exp2, EMPTY_MAP), is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, EMPTY_MAP, exp3, EMPTY_MAP), is(false));
        assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, EMPTY_MAP, ent1, EMPTY_MAP), is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, EMPTY_MAP, ent2, EMPTY_MAP), is(false));
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, EMPTY_MAP, ent1, EMPTY_MAP), is(false));

        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(exp1, EMPTY_MAP, null, EMPTY_MAP));
        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(null, EMPTY_MAP, exp2, EMPTY_MAP));
        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(exp1, null, exp2, EMPTY_MAP));
        assertThrows(NullPointerException.class,
                () -> EquivalenceAndHashUtil.areEquivalent(exp1, EMPTY_MAP, exp2, null));

        assertThrows(NullPointerException.class, () -> new Literal((Bool) null));

    }
    
    @Test
    void testAreEquivalent2() throws Exception {
        var exp4 = expression("{\"a\":\"b\"}['a']");
        var exp5 = expression("{\"a\":\"b\"}['a']");
        var eList2 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList2.size()).thenReturn(2);
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp4, EMPTY_MAP, exp5, EMPTY_MAP), is(true));
        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(exp4, EMPTY_MAP, eList2, EMPTY_MAP), is(false));
    }

    @Test
    void testFeaturesAreEquivalent() throws IOException {
        var objectMock = mock(Object.class);
        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(objectMock, EMPTY_MAP, objectMock, EMPTY_MAP),
                is(true));

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(objectMock, EMPTY_MAP, null, EMPTY_MAP), is(false));

        var eList1 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList1.size()).thenReturn(1);

        var eList2 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList2.size()).thenReturn(2);

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, EMPTY_MAP, eList2, EMPTY_MAP), is(false));

        var iter1 = mock(Iterator.class);
        when(eList1.size()).thenReturn(2);
        when(eList1.iterator()).thenReturn(iter1);
        when(iter1.hasNext()).thenReturn(Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);
        when(iter1.next()).thenReturn(objectMock, objectMock, objectMock);

        var iter2 = mock(Iterator.class);
        when(eList2.size()).thenReturn(2);
        when(eList2.iterator()).thenReturn(iter2);
        when(iter2.hasNext()).thenReturn(Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);
        when(iter2.next()).thenReturn(objectMock, null, objectMock);

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, EMPTY_MAP, eList2, EMPTY_MAP), is(false));

        var expB = expression("mock()");
        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, EMPTY_MAP, expB, EMPTY_MAP), is(false));

    }
    
    

}
