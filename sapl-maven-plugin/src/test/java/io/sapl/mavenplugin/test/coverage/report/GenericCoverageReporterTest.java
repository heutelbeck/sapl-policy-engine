package io.sapl.mavenplugin.test.coverage.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.interpreter.DefaultSAPLInterpreter;
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
	public void test() throws IOException {
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
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		//act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		//assert
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(11);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(5).getLineNumber()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(5).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(6).getLineNumber()).isEqualTo(6);
		Assertions.assertThat(docs.get(0).getLine(6).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(7).getLineNumber()).isEqualTo(7);
		Assertions.assertThat(docs.get(0).getLine(7).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(8).getLineNumber()).isEqualTo(8);
		Assertions.assertThat(docs.get(0).getLine(8).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(9).getLineNumber()).isEqualTo(9);
		Assertions.assertThat(docs.get(0).getLine(9).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(10).getLineNumber()).isEqualTo(10);
		Assertions.assertThat(docs.get(0).getLine(10).getCoveredValue()).isEqualTo(LineCoveredValue.PARTLY);
		Assertions.assertThat(docs.get(0).getLine(11).getLineNumber()).isEqualTo(11);
		Assertions.assertThat(docs.get(0).getLine(11).getCoveredValue()).isEqualTo(LineCoveredValue.PARTLY);
	}
	
	@Test
	public void test_policySetWithTarget() {
		//arrange
		String sapl = "set \"set\" \ndeny-unless-permit \nfor action == \"read\" \npolicy \"policy1\" \npermit";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.NEVER);
		Assertions.assertThat(docs.get(0).getLine(5).getLineNumber()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(5).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
	}
	
	@Test
	public void test_policySetWithValue() {
		//arrange
		String sapl = "set \"set\" \ndeny-unless-permit \nvar temp = 1; \npolicy \"policy1\" \npermit";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.NEVER);
		Assertions.assertThat(docs.get(0).getLine(5).getLineNumber()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(5).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
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
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
	}
	
	@Test
	public void test_policyWithUncoveredStatement() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;\ntrue;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit = new PolicyConditionHit("", "policy1", 0, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit));
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.PARTLY);
		Assertions.assertThat(docs.get(0).getLine(5).getLineNumber()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(5).getCoveredValue()).isEqualTo(LineCoveredValue.NEVER);
	}

	@Test
	public void test_policyBodyValueDefinitionFULLY_notDependingOnFollowingCondition() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\nvar id=1;\ntrue;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 1, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(5).getLineNumber()).isEqualTo(5);
		Assertions.assertThat(docs.get(0).getLine(5).getCoveredValue()).isEqualTo(LineCoveredValue.PARTLY);
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
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		//expect covered information for second condition on last line to overwrite covered information for first condition
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.PARTLY);
	}
	
	@Test
	public void test_policyBodyMultipleStatementsPerLine_WithValue() {
		//arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;var id=1;";
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));
		GenericCoverageReporter reporter = new GenericCoverageReporter(documents, hits);
		
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage();
		
		Assertions.assertThat(docs.size()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLineCount()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(1).getLineNumber()).isEqualTo(1);
		Assertions.assertThat(docs.get(0).getLine(1).getCoveredValue()).isEqualTo(LineCoveredValue.FULLY);
		Assertions.assertThat(docs.get(0).getLine(2).getLineNumber()).isEqualTo(2);
		Assertions.assertThat(docs.get(0).getLine(2).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		Assertions.assertThat(docs.get(0).getLine(3).getLineNumber()).isEqualTo(3);
		Assertions.assertThat(docs.get(0).getLine(3).getCoveredValue()).isEqualTo(LineCoveredValue.UNINTERESTING);
		//expect covered information for second condition on last line to overwrite covered information for first condition
		Assertions.assertThat(docs.get(0).getLine(4).getLineNumber()).isEqualTo(4);
		Assertions.assertThat(docs.get(0).getLine(4).getCoveredValue()).isEqualTo(LineCoveredValue.PARTLY);
	}
	
	

}
