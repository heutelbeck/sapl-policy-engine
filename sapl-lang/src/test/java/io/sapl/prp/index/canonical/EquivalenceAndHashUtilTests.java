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
package io.sapl.prp.index.canonical;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import org.eclipse.emf.common.util.EList;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;

import static io.sapl.testutil.ParserUtil.entitlement;
import static io.sapl.testutil.ParserUtil.expression;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EquivalenceAndHashUtilTests {
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void testSemanticHash() throws Exception {
        final var sapl1 = INTERPRETER.parse("""
                policy "p1"
                permit io.sapl.mock()
                """);

        final var sapl2 = INTERPRETER.parse("""
                import io.sapl.mock
                policy "p1"
                permit mock()
                """);

        final var sapl3 = INTERPRETER.parse("""
                policy "p1"
                permit mock()
                """);

        final var expA = sapl1.getPolicyElement().getTargetExpression();
        final var expB = sapl2.getPolicyElement().getTargetExpression();
        final var expC = sapl3.getPolicyElement().getTargetExpression();

        final var hashA = EquivalenceAndHashUtil.semanticHash(expA);
        final var hashB = EquivalenceAndHashUtil.semanticHash(expB);
        final var hashC = EquivalenceAndHashUtil.semanticHash(expC);

        assertThat(hashA, is(hashB));
        assertThat(hashA, not(is(hashC)));

        final var exp1 = expression("exp");
        final var exp2 = expression("exp");
        final var ent1 = entitlement("permit");
        final var ent2 = entitlement("deny");

        final var hash1 = EquivalenceAndHashUtil.semanticHash(exp1);
        final var hash2 = EquivalenceAndHashUtil.semanticHash(exp2);
        final var hash3 = EquivalenceAndHashUtil.semanticHash(ent1);
        final var hash4 = EquivalenceAndHashUtil.semanticHash(ent2);

        assertThat(hash1, is(hash2));
        assertThat(hash1, not(is(hash3)));
        assertThat(hash1, not(is(hash4)));
        assertThat(hash3, not(is(hash4)));
    }

    @Test
    void testAreEquivalent() throws Exception {
        final var sapl1 = INTERPRETER.parse("""
                policy "p1"
                permit io.sapl.mock()
                """);

        final var sapl2 = INTERPRETER.parse("""
                import io.sapl.mock
                policy "p1"
                permit mock()
                """);

        final var expA = sapl1.getPolicyElement().getTargetExpression();
        final var expB = sapl2.getPolicyElement().getTargetExpression();
        assertThat(EquivalenceAndHashUtil.areEquivalent(expA, expB), is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(expB, expB), is(true));

        final var exp1 = expression("exp1");
        final var exp2 = expression("exp1");
        final var exp3 = expression("exp2");
        final var ent1 = entitlement("permit");
        final var ent2 = entitlement("deny");

        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, exp2), is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, exp3), is(false));
        assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, ent1), is(true));
        assertThat(EquivalenceAndHashUtil.areEquivalent(ent1, ent2), is(false));
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp1, ent1), is(false));

        assertThrows(NullPointerException.class, () -> EquivalenceAndHashUtil.areEquivalent(exp1, null));
        assertThrows(NullPointerException.class, () -> EquivalenceAndHashUtil.areEquivalent(null, exp2));

        assertThrows(NullPointerException.class, () -> new Literal((Bool) null));

    }

    @Test
    void testAreEquivalent2() throws Exception {
        final var exp4   = expression("{\"a\":\"b\"}['a']");
        final var exp5   = expression("{\"a\":\"b\"}['a']");
        final var eList2 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList2.size()).thenReturn(2);
        assertThat(EquivalenceAndHashUtil.areEquivalent(exp4, exp5), is(true));
        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(exp4, eList2), is(false));
    }

    @Test
    void testFeaturesAreEquivalent() throws IOException {
        final var objectMock = mock(Object.class);
        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(objectMock, objectMock), is(true));

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(objectMock, null), is(false));

        final var eList1 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList1.size()).thenReturn(1);

        final var eList2 = mock(EList.class, RETURNS_DEEP_STUBS);
        when(eList2.size()).thenReturn(2);

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, eList2), is(false));

        final var iter1 = mock(Iterator.class);
        when(eList1.size()).thenReturn(2);
        when(eList1.iterator()).thenReturn(iter1);
        when(iter1.hasNext()).thenReturn(Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);
        when(iter1.next()).thenReturn(objectMock, objectMock, objectMock);

        final var iter2 = mock(Iterator.class);
        when(eList2.size()).thenReturn(2);
        when(eList2.iterator()).thenReturn(iter2);
        when(iter2.hasNext()).thenReturn(Boolean.TRUE, Boolean.TRUE, Boolean.FALSE);
        when(iter2.next()).thenReturn(objectMock, null, objectMock);

        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, eList2), is(false));

        final var expB = expression("mock()");
        assertThat(EquivalenceAndHashUtil.featuresAreEquivalent(eList1, expB), is(false));

    }

}
