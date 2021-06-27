package io.sapl.mavenplugin.test.coverage.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class GenericCoverageReporterTest {

	private DefaultSAPLInterpreter INTERPRETER;
	
	@BeforeEach
	public void setup() {
		this.INTERPRETER = new DefaultSAPLInterpreter();
	}
	
	@Test
	public void test_standard() throws IOException {
		//arrange
		Path path = Paths.get("target/classes/policies/policy_1.sapl");
		String sapl = Files.readString(path);
		int lineCount = Files.readAllLines(path).size();
		Collection<SaplDocument> documents = List.of(new SaplDocument(path, lineCount, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("testPolicies");
		PolicyHit policyHit = new PolicyHit("testPolicies", "policy 1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("testPolicies", "policy 1", 0, true);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("testPolicies", "policy 1", 1, true);
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(policyHit), List.of(conditionHit1, conditionHit2));
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		//act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		//assert
		assertEquals(1, docs.size());
		assertEquals(11, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(5).getCoveredValue());
		assertEquals(6, docs.get(0).getLine(6).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(6).getCoveredValue());
		assertEquals(7, docs.get(0).getLine(7).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(7).getCoveredValue());
		assertEquals(8, docs.get(0).getLine(8).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(8).getCoveredValue());
		assertEquals(9, docs.get(0).getLine(9).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(9).getCoveredValue());
		assertEquals(10, docs.get(0).getLine(10).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(10).getCoveredValue());
		assertEquals(11, docs.get(0).getLine(11).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(11).getCoveredValue());
	}
	
	@Test
	public void test_policySetWithTarget() {
		//arrange
		String sapl = "set \"set\" \ndeny-unless-permit \nfor action == \"read\" \npolicy \"policy1\" \npermit";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		assertEquals(1, docs.size());
		assertEquals(5, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(5).getCoveredValue());
	}
	
	@Test
	public void test_policyWithInvalidType() {
		String sapl = "set \"set\" \ndeny-unless-permit \nvar temp = 1; \npolicy \"policy1\" \npermit";
		SAPL mockSAPL = Mockito.mock(SAPL.class);
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, mockSAPL));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		PolicyElement mockPolicyElement = Mockito.mock(PolicyElement.class);
		
		Mockito.when(mockSAPL.getPolicyElement()).thenReturn(mockPolicyElement);
		Mockito.when(mockPolicyElement.eClass()).thenReturn(SaplPackage.Literals.POLICY_BODY);
		
		assertThrows(SaplTestException.class, () -> reporter.calcDocumentCoverage(documents, hits));
		
		
		
	}
	
	@Test
	public void test_policySetWithValue() {
		//arrange
		String sapl = "set \"set\" \ndeny-unless-permit \nvar temp = 1; \npolicy \"policy1\" \npermit";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		assertEquals(1, docs.size());
		assertEquals(5, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(5).getCoveredValue());
	}
	
	@Test
	public void test_policyWithoutTarget() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1, conditionHit2));
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
	}
	
	@Test
	public void test_policyWithUncoveredStatement() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;\ntrue;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit = new PolicyConditionHit("", "policy1", 0, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit));
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		assertEquals(1, docs.size());
		assertEquals(5, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(5).getCoveredValue());
	}

	@Test
	public void test_policyBodyValueDefinitionFULLY_notDependingOnFollowingCondition() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\nvar id=1;\ntrue;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 1, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		assertEquals(1, docs.size());
		assertEquals(5, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(5).getCoveredValue());
	}
	
	@Test
	public void test_policyBodyMultipleStatementsPerLine() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1, conditionHit2));
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(3).getCoveredValue());
		//expect covered information for second condition on last line to overwrite covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
	}
	
	@Test
	public void test_policyBodyMultipleStatementsPerLine_WithValue() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;var id=1;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));
		GenericCoverageReporter reporter = new GenericCoverageReporter();
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);
		
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.UNINTERESTING, docs.get(0).getLine(3).getCoveredValue());
		//expect covered information for second condition on last line to overwrite covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
	}
	
	

}
