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
package io.sapl.grammar.ide.contentassist;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.emf.ecore.impl.MinimalEObjectImpl;
import org.junit.jupiter.api.Test;

import io.sapl.grammar.sapl.impl.SAPLImpl;

public class TreeNavigatorHelperTests {

	public class TestEObject extends MinimalEObjectImpl.Container {
	}

	@Test
	public void test_goToFirstParent_objectIsNull_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> {
			TreeNavigationHelper.goToFirstParent(null, Object.class);
		});
	}

	@Test
	public void test_goToFirstParent_classTypeIsNull_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> {
			TreeNavigationHelper.goToFirstParent(new TestEObject(), null);
		});
	}

	@Test
	public void test_goToFirstParent_objectIsWrongClassAndHasNoEContainer_returnsNull() {
		Object result = TreeNavigationHelper.goToFirstParent(new TestEObject(), SAPLImpl.class);
		assertNull(result);
	}

	@Test
	public void test_goToLastParent_objectIsNull_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> {
			TreeNavigationHelper.goToLastParent(null, Object.class);
		});
	}

	@Test
	public void test_goToLastParent_classTypeIsNull_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> {
			TreeNavigationHelper.goToLastParent(new TestEObject(), null);
		});
	}

	@Test
	public void test_goToLastParent_objectIsWrongClassAndHasNoEContainer_returnsNull() {
		Object result = TreeNavigationHelper.goToLastParent(new TestEObject(), SAPLImpl.class);
		assertNull(result);
	}
}
