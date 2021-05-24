package io.sapl.mavenplugin.test.coverage.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class CoverageTargetHelperTest {
	
	private Collection<SaplDocument> documents;
	
	@BeforeEach
	public void setup() {
		String policyPath = "policies";
		MavenProjectStub project = new MavenProjectStub();
		//project.setTestClasspathElements(List.of("C:/Users/Nikolai/eclipse-sapl-workspace/sapl-test/sapl-maven-plugin/target/test-classes"));
		project.setRuntimeClasspathElements(List.of("target/classes"));
		SaplDocumentReader reader = new SaplDocumentReader();
		this.documents = reader.retrievePolicyDocuments(new SilentLog(), project, policyPath);
	}
	
	
	@Test
    public void testPolicyRetrieval() throws Exception {
		CoverageTargetHelper helper = new CoverageTargetHelper();
		CoverageTargets targets = helper.getCoverageTargets(this.documents);
		assertEquals(1, targets.getPolicySets().size());
		assertEquals(true, targets.isPolicySetHit(new PolicySetHit("testPolicies")));
		assertEquals(2, targets.getPolicys().size());
		assertEquals(true, targets.isPolicyHit(new PolicyHit("testPolicies", "policy 1")));
		assertEquals(true, targets.isPolicyHit(new PolicyHit("", "policy 2")));
		assertEquals(4, targets.getPolicyConditions().size());
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 0, true)));
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 0, false)));
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 1, true)));
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 1, false)));
	}
	
}
