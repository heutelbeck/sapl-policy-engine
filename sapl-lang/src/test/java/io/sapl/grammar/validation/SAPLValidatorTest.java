/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.validation;

import com.google.inject.Inject;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.tests.SAPLInjectorProvider;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.extensions.InjectionExtension;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.testing.validation.ValidationTestHelper;
import org.eclipse.xtext.xbase.lib.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * tests for custom validator class SAPLValidator
 */
@ExtendWith(InjectionExtension.class)
@InjectWith(SAPLInjectorProvider.class)
public class SAPLValidatorTest {

	@Inject
	@Extension
	private ParseHelper<SAPL> parseHelper;

	@Inject
	@Extension
	private ValidationTestHelper validator;

	@Test
	public void targetWithEagerOpsPermit() throws Exception {
		String testPolicy = "policy \"test policy\" permit a == b & c == d | e > f";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertNoErrors(policy);
	}

	@Test
	public void targetWithLazyAnd() throws Exception {
		String testPolicy = "policy \"test policy\" permit a == b && c == d | e > f";
		SAPL policy = this.parseHelper.parse(testPolicy);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getAnd(), null,
				SAPLValidator.MSG_AND_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	public void targetWithLazyOr() throws Exception {
		String policyText = "policy \"test policy\" permit a == b & c == d || e > f";
		SAPL policy = this.parseHelper.parse(policyText);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getOr(), null,
				SAPLValidator.MSG_OR_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	public void targetWithAttributeFinderStep() throws Exception {
		String policyText = "policy \"test policy\" permit action.patientid.<pip.hospital_units.by_patientid>.doctorid == \"Brinkmann\"";
		SAPL policy = this.parseHelper.parse(policyText);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getAttributeFinderStep(), null,
				SAPLValidator.MSG_AFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	public void policyWithHeadAttributeFinderStepAssertsNoError() throws Exception {
		String policyText = "policy \"test policy\" permit action.patientid.|<pip.hospital_units.by_patientid>.doctorid == \"Brinkmann\"";
		SAPL policy = this.parseHelper.parse(policyText);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getHeadAttributeFinderStep(), null,
				SAPLValidator.MSG_HAFS_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	public void policyWithBasicEnvironmentAttributeAssertsNoError() throws Exception {
		String policyText = "policy \"test policy\" permit <pip.hospital_units.by_patientid>.doctorid == \"Brinkmann\"";
		SAPL policy = this.parseHelper.parse(policyText);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getBasicEnvironmentAttribute(), null,
				SAPLValidator.MSG_BEA_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	public void policyWithBasicEnvironmentHeadAttributeAssertsNoError() throws Exception {
		String policyText = "policy \"test policy\" permit |<pip.hospital_units.by_patientid>.doctorid == \"Brinkmann\"";
		SAPL policy = this.parseHelper.parse(policyText);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getBasicEnvironmentHeadAttribute(), null,
				SAPLValidator.MSG_BEHA_IS_NOT_ALLOWED_IN_TARGET_EXPRESSION);
	}

	@Test
	public void invalidPolicy() throws Exception {
		String policyText = "defect";
		SAPL policy = this.parseHelper.parse(policyText);
		this.validator.assertError(policy, SaplPackage.eINSTANCE.getSAPL(), Diagnostic.SYNTAX_DIAGNOSTIC,
				"no viable alternative at input \'defect\'");
	}

}
