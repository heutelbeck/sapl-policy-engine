package io.sapl.mavenplugin.test.coverage.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.eclipse.emf.ecore.impl.EClassImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;
import org.mockito.Mockito;

public class CoverageTargetHelperTest {
	
	private Collection<SaplDocument> documents;
	private CoverageTargetHelper helper;
	
	@BeforeEach
	public void setup() throws MojoExecutionException {
		String policyPath = "policies";
		MavenProjectStub project = new MavenProjectStub();
		project.setRuntimeClasspathElements(List.of("target/classes"));
		SaplDocumentReader reader = new SaplDocumentReader();
		this.documents = reader.retrievePolicyDocuments(new SilentLog(), project, policyPath);
		this.helper = new CoverageTargetHelper();
	}
	
	
	@Test
    public void testPolicyRetrieval() {
		CoverageTargets targets = helper.getCoverageTargets(this.documents);
		assertEquals(1, targets.getPolicySets().size());
		assertEquals(true, targets.isPolicySetHit(new PolicySetHit("testPolicies")));
		assertEquals(2, targets.getPolicys().size());
		assertEquals(true, targets.isPolicyHit(new PolicyHit("testPolicies", "policy 1")));
		assertEquals(true, targets.isPolicyHit(new PolicyHit("", "policy 2")));
		assertEquals(4, targets.getPolicyConditions().size());
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 0, true)));
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 0, false)));
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 2, true)));
		assertEquals(true, targets.isPolicyConditionHit(new PolicyConditionHit("testPolicies", "policy 1", 2, false)));

	}

	@Test
	public void test_newSAPLType() {
		SAPL newPolicyTypeSAPL = Mockito.mock(SAPL.class);
		PolicyElement newPolicyTypePolicyElement = Mockito.mock(PolicyElement.class);
		Mockito.when(newPolicyTypeSAPL.getPolicyElement()).thenReturn(newPolicyTypePolicyElement);
		//return SaplPackage.Literals.POLICY_ELEMENT as synonym for a new unknown implementation of PolicyElement 
		Mockito.when(newPolicyTypePolicyElement.eClass()).thenReturn(SaplPackage.Literals.POLICY_ELEMENT);
		SaplDocument newSaplTypeDocument = new SaplDocument(Path.of("test.sapl"), 5, newPolicyTypeSAPL);
		
		assertThrows(SaplTestException.class, () -> helper.getCoverageTargets(List.of(newSaplTypeDocument)));
	}	
}
