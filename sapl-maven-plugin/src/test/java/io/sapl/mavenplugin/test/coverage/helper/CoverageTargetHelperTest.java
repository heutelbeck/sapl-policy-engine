package io.sapl.mavenplugin.test.coverage.helper;

import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import io.sapl.mavenplugin.test.coverage.model.CoverageHitSummary;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;

public class CoverageTargetHelperTest {
	
	private Collection<SaplDocument> documents;
	
	@Before
	public void setup() {
		String policyPath = "policies";
		MavenProjectStub project = new MavenProjectStub();
		//project.setTestClasspathElements(List.of("C:/Users/Nikolai/eclipse-sapl-workspace/sapl-test/sapl-maven-plugin/target/test-classes"));
		project.setRuntimeClasspathElements(List.of("target/classes"));
		SaplDocumentReader reader = new SaplDocumentReader(new SilentLog(), project);
		this.documents = reader.retrievePolicyDocuments(policyPath);
	}
	
	
	@Test
    public void testPolicyRetrieval() throws Exception {
		CoverageTargetHelper helper = new CoverageTargetHelper();
		CoverageHitSummary targets = helper.getCoverageTargets(this.documents);
		Assertions.assertThat(targets.getPolicySets().size()).isEqualTo(1);
		Assertions.assertThat(targets.isPolicySetHit("testPolicies")).isTrue();
		Assertions.assertThat(targets.getPolicys().size()).isEqualTo(2);
		Assertions.assertThat(targets.isPolicyHit("testPolicies", "policy 1")).isTrue();
		Assertions.assertThat(targets.isPolicyHit("", "policy 2")).isTrue();
		Assertions.assertThat(targets.getPolicyConditions().size()).isEqualTo(4);
		Assertions.assertThat(targets.isPolicyConditionHit("testPolicies", "policy 1", 0, true)).isTrue();
		Assertions.assertThat(targets.isPolicyConditionHit("testPolicies", "policy 1", 0, false)).isTrue();
		Assertions.assertThat(targets.isPolicyConditionHit("testPolicies", "policy 1", 1, true)).isTrue();
		Assertions.assertThat(targets.isPolicyConditionHit("testPolicies", "policy 1", 1, false)).isTrue();
	}
	
}
