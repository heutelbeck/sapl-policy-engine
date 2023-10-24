/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mavenplugin.test.coverage.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageTargetHelperTests {

	private Collection<SaplDocument> documents;

	private CoverageTargetHelper helper;

	@BeforeEach
	void setup() throws MojoExecutionException {
		String           policyPath = "policies";
		MavenProjectStub project    = new MavenProjectStub();
		project.setRuntimeClasspathElements(List.of("target/classes"));
		SaplDocumentReader reader = new SaplDocumentReader();
		this.documents = reader.retrievePolicyDocuments(new SilentLog(), project, policyPath);
		this.helper    = new CoverageTargetHelper();
	}

	@Test
	void testPolicyRetrieval() {
		CoverageTargets targets = helper.getCoverageTargets(this.documents);
		assertEquals(1, targets.getPolicySets().size());
		assertEquals(Boolean.TRUE, targets.isPolicySetHit(new PolicySetHit("testPolicies")));
		assertEquals(2, targets.getPolicies().size());
		assertEquals(Boolean.TRUE, targets.isPolicyHit(new PolicyHit("testPolicies", "policy 1")));
		assertEquals(Boolean.TRUE, targets.isPolicyHit(new PolicyHit("", "policy 2")));
		assertEquals(4, targets.getPolicyConditions().size());
		assertEquals(Boolean.TRUE,
				targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 0, true)));
		assertEquals(Boolean.TRUE,
				targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 0, false)));
		assertEquals(Boolean.TRUE,
				targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 2, true)));
		assertEquals(Boolean.TRUE,
				targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 2, false)));

	}

	@Test
	void test_newSAPLType() {
		SAPL          newPolicyTypeSAPL          = Mockito.mock(SAPL.class);
		PolicyElement newPolicyTypePolicyElement = Mockito.mock(PolicyElement.class);
		Mockito.when(newPolicyTypeSAPL.getPolicyElement()).thenReturn(newPolicyTypePolicyElement);
		// return SaplPackage.Literals.POLICY_ELEMENT as synonym for a new unknown
		// implementation of PolicyElement
		Mockito.when(newPolicyTypePolicyElement.eClass()).thenReturn(SaplPackage.Literals.POLICY_ELEMENT);
		var newSaplTypeDocument = new SaplDocument(Path.of("test.sapl"), 5, newPolicyTypeSAPL);
		var listOfDocs          = List.of(newSaplTypeDocument);
		assertThrows(SaplTestException.class, () -> helper.getCoverageTargets(listOfDocs));
	}

}
