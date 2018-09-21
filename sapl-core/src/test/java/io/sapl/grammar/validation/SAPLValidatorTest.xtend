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
		'''.parse.assertError(SaplPackage::eINSTANCE.and, null, "And is not allowed in target expression.");
	}

	@Test
	def void targetWithLazyOr() {
		'''
			policy "test policy" permit a == b & c == d || e > f
		'''.parse.assertError(SaplPackage::eINSTANCE.or, null, "Or is not allowed in target expression.");
	}

	@Test
	def void targetWithAttributeFinderStep() {
		'''
			policy "test policy" permit action.patientid.<pip.hospital_units.by_patientid>.doctorid == "Brinkmann"
		'''.parse.assertError(SaplPackage::eINSTANCE.attributeFinderStep, null, "AttributeFinderStep is not allowed in target expression.");
	}
	
	@Test	
	def void invalidPolicy() {
		'''
			defect
		'''.parse.assertError(SaplPackage::eINSTANCE.SAPL, Diagnostic.SYNTAX_DIAGNOSTIC, "no viable alternative at input 'defect'");
	}

}
