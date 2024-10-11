/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide.contentassist;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;
import org.junit.jupiter.api.Test;

import io.sapl.grammar.sapl.impl.SAPLImpl;

class TreeNavigatorHelperTests {

    public static class TestEObject extends MinimalEObjectImpl.Container {

    }

    @Test
    void test_goToFirstParent_objectIsNull_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TreeNavigationUtil.goToFirstParent(null, Object.class));
    }

    @Test
    void test_goToFirstParent_classTypeIsNull_throwsIllegalArgumentException() {
        final var testObject = new TestEObject();
        assertThrows(IllegalArgumentException.class, () -> TreeNavigationUtil.goToFirstParent(testObject, null));
    }

    @Test
    void test_goToFirstParent_objectIsWrongClassAndHasNoEContainer_returnsNull() {
        Object result = TreeNavigationUtil.goToFirstParent(new TestEObject(), SAPLImpl.class);
        assertNull(result);
    }

    @Test
    void test_goToFirstParent_objectHasRequestedClass_returnsObject() {
        final var expectedObject = new TestEObject();
        Object    result         = TreeNavigationUtil.goToFirstParent(expectedObject, TestEObject.class);
        assertSame(expectedObject, result);
    }

    @Test
    void test_goToLastParent_objectIsNull_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TreeNavigationUtil.goToLastParent(null, Object.class));
    }

    @Test
    void test_goToLastParent_classTypeIsNull_throwsIllegalArgumentException() {
        final var testObject = new TestEObject();
        assertThrows(IllegalArgumentException.class, () -> TreeNavigationUtil.goToLastParent(testObject, null));
    }

    @Test
    void test_goToLastParent_objectIsWrongClassAndHasNoEContainer_returnsNull() {
        Object result = TreeNavigationUtil.goToLastParent(new TestEObject(), SAPLImpl.class);
        assertNull(result);
    }

    @Test
    void test_goToLastParent_objectHasRequestedClass_returnsObject() {
        final var expectedObject = new TestEObject();
        Object    result         = TreeNavigationUtil.goToLastParent(expectedObject, TestEObject.class);
        assertSame(expectedObject, result);
    }

}
