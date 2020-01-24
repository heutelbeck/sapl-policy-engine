/**
 * Copyright © 2019 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
package io.sapl.grammar.validation

import com.google.inject.Inject
import io.sapl.grammar.sapl.SAPL
import io.sapl.grammar.sapl.SaplPackage
import io.sapl.grammar.tests.SAPLInjectorProvider
import org.eclipse.xtext.diagnostics.Diagnostic
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.XtextRunner
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.Test
import org.junit.runner.RunWith


/**
 * tests for custom validator class SAPLValidator
 */
@RunWith(XtextRunner)
@InjectWith(SAPLInjectorProvider)
class SAPLValidatorTest {

	@Inject extension ParseHelper<SAPL> parseHelper
	@Inject extension ValidationTestHelper validator

	@Test
	def void targetWithEagerOpsPermit() {
		'''
			policy "test policy" permit a == b & c == d | e > f
		'''.parse.assertNoErrors
	}

	@Test
	def void targetWithLazyAnd() {
		'''
			policy "test policy" permit a == b && c == d | e > f
		'''.parse.assertError(SaplPackage::eINSTANCE.and, null, SAPLValidator.MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	def void targetWithLazyOr() {
		'''
			policy "test policy" permit a == b & c == d || e > f
		'''.parse.assertError(SaplPackage::eINSTANCE.or, null, SAPLValidator.MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	def void targetWithAttributeFinderStep() {
		'''
			policy "test policy" permit action.patientid.<pip.hospital_units.by_patientid>.doctorid == "Brinkmann"
		'''.parse.assertError(SaplPackage::eINSTANCE.attributeFinderStep, null, SAPLValidator.MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}
	
	@Test	
	def void invalidPolicy() {
		'''
			defect
		'''.parse.assertError(SaplPackage::eINSTANCE.SAPL, Diagnostic.SYNTAX_DIAGNOSTIC, "no viable alternative at input 'defect'");
	}

}
