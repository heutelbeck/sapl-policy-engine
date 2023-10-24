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
package io.sapl.mavenplugin.test.coverage.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.grammar.sapl.PolicyElement;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.Statement;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import io.sapl.mavenplugin.test.coverage.report.model.LineCoveredValue;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentCoverageInformation;
import io.sapl.mavenplugin.test.coverage.report.model.SaplDocumentLineCoverageInformation;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

class GenericCoverageReporterTests {

	private DefaultSAPLInterpreter INTERPRETER;

	private GenericCoverageReporter reporter;

	@BeforeEach
	void setup() {
		this.reporter = new GenericCoverageReporter();
		this.INTERPRETER = new DefaultSAPLInterpreter();
	}

	@Test
	void test_standard() throws IOException {
		// arrange
		Path path = Paths.get("target/classes/policies/policy_1.sapl");
		String sapl = Files.readString(path);
		int lineCount = Files.readAllLines(path).size();
		Collection<SaplDocument> documents = List.of(new SaplDocument(path, lineCount, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("testPolicies");
		PolicyHit policyHit = new PolicyHit("testPolicies", "policy 1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("testPolicies", "policy 1", 0, true);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("testPolicies", "policy 1", 2, true);
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(policyHit),
				List.of(conditionHit1, conditionHit2));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(12, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(5).getCoveredValue());
		assertEquals(6, docs.get(0).getLine(6).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(6).getCoveredValue());
		assertEquals(7, docs.get(0).getLine(7).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(7).getCoveredValue());
		assertEquals(8, docs.get(0).getLine(8).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(8).getCoveredValue());
		assertEquals(9, docs.get(0).getLine(9).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(9).getCoveredValue());
		assertEquals(10, docs.get(0).getLine(10).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(10).getCoveredValue());
		assertEquals(11, docs.get(0).getLine(11).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(11).getCoveredValue());
		assertEquals(12, docs.get(0).getLine(12).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(12).getCoveredValue());
	}

	@Test
	void test_policySetNotHit() {
		// arrange
		String sapl = "set \"set\" \ndeny-unless-permit \npolicy \"policy1\" \npermit";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(), List.of());

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(4).getCoveredValue());
	}

	@Test
	void test_policySetWithTarget() {
		// arrange
		String sapl = "set \"set\" \ndeny-unless-permit \nfor action == \"read\" \npolicy \"policy1\" \npermit";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
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
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(5).getCoveredValue());
	}

	@Test
	void test_policyWithInvalidType() {
		SAPL mockSAPL = Mockito.mock(SAPL.class);
		Collection<SaplDocument> documents = List.of(new SaplDocument(Paths.get("test.sapl"), 5, mockSAPL));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());
		PolicyElement mockPolicyElement = Mockito.mock(PolicyElement.class);

		Mockito.when(mockSAPL.getPolicyElement()).thenReturn(mockPolicyElement);
		Mockito.when(mockPolicyElement.eClass()).thenReturn(SaplPackage.Literals.POLICY_BODY);

		// act
		// // assert
		assertThrows(SaplTestException.class, () -> reporter.calcDocumentCoverage(documents, hits));

	}

	@Test
	void test_policySetWithValue() {
		// arrange
		String sapl = "set \"set\" \ndeny-unless-permit \nvar temp = 1; \npolicy \"policy1\" \npermit";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicySetHit setHit = new PolicySetHit("set");
		CoverageTargets hits = new CoverageTargets(List.of(setHit), List.of(), List.of());

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
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
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(5).getCoveredValue());
	}

	@Test
	void test_policyWithoutTarget() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
	}

	@Test
	void test_policyWithUncoveredStatement() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;\ntrue;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit = new PolicyConditionHit("", "policy1", 0, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(5, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(5).getCoveredValue());
	}

	@Test
	void test_policyBodyValueDefinition_lastStatementNotHit() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\nfalse;\nvar id=1;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(5, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(5).getCoveredValue());
	}

	@Test
	void test_policyBodyValueDefinitionFULLY_notDependingOnFollowingCondition() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\nvar id=1;\ntrue;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 5, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 1, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(5, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(5, docs.get(0).getLine(5).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(5).getCoveredValue());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markFullyWhenFully() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 0, true);
		PolicyConditionHit conditionHit3 = new PolicyConditionHit("", "policy1", 1, false);
		PolicyConditionHit conditionHit4 = new PolicyConditionHit("", "policy1", 1, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2, conditionHit3, conditionHit4));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(4, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markFullyWhenPartly() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 1, true);
		PolicyConditionHit conditionHit3 = new PolicyConditionHit("", "policy1", 1, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2, conditionHit3));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(3, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markFullyWhenNever() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 1, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 1, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2));

		// act
		// assert
		assertThrows(SaplTestException.class, () -> reporter.calcDocumentCoverage(documents, hits));
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markPartlyWhenFully() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 0, true);
		PolicyConditionHit conditionHit3 = new PolicyConditionHit("", "policy1", 1, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2, conditionHit3));
		GenericCoverageReporter reporter = new GenericCoverageReporter();

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(3, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markPartlyWhenPartly() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, true);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 1, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(2, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markPartlyWhenNever() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 1, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));

		// act
		// assert
		assertThrows(SaplTestException.class, () -> reporter.calcDocumentCoverage(documents, hits));
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markNeverWhenFully() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(2, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markNeverWhenPartly() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(1, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_markNeverWhenNever() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;true;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of());

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(4, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(0, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_WithValue_markFullyWhenFully() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;var id=1;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, true);
		PolicyConditionHit conditionHit2 = new PolicyConditionHit("", "policy1", 0, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit),
				List.of(conditionHit1, conditionHit2));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(2, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_WithValue_markFullyWhenPartly() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;var id=1;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, true);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(1, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_WithValue_markFullyWhenNever() {
		// cannot be reached due to isLastStatementHit if clause

		SaplDocumentCoverageInformation coverage = new SaplDocumentCoverageInformation(null, 1);
		coverage.markLine(1, LineCoveredValue.NEVER, 1, 1);
		assertThrows(SaplTestException.class, () -> this.reporter.markValueFULLY(coverage, 1, 1));
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_WithValue_markNeverWhenFully() {
		// cannot normally be reached due to isLastStatementHit if clause

		SaplDocumentCoverageInformation coverage = new SaplDocumentCoverageInformation(null, 1);
		coverage.markLine(1, LineCoveredValue.FULLY, 1, 1);
		assertThrows(SaplTestException.class, () -> this.reporter.markValueNEVER(coverage, 1, 1));
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_WithValue_markNeverWhenPartly() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;var id=1;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		PolicyConditionHit conditionHit1 = new PolicyConditionHit("", "policy1", 0, false);
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of(conditionHit1));

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.PARTLY, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(1, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void test_policyBodyMultipleStatementsPerLine_WithValue_markNeverWhenNever() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;var id=1;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of());

		// act
		List<SaplDocumentCoverageInformation> docs = reporter.calcDocumentCoverage(documents, hits);

		// assert
		assertEquals(1, docs.size());
		assertEquals(4, docs.get(0).getLineCount());
		assertEquals(1, docs.get(0).getLine(1).getLineNumber());
		assertEquals(LineCoveredValue.FULLY, docs.get(0).getLine(1).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(2).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(2).getCoveredValue());
		assertEquals(3, docs.get(0).getLine(3).getLineNumber());
		assertEquals(LineCoveredValue.IRRELEVANT, docs.get(0).getLine(3).getCoveredValue());
		// expect covered information for second condition on last line to overwrite
		// covered information for first condition
		assertEquals(4, docs.get(0).getLine(4).getLineNumber());
		assertEquals(LineCoveredValue.NEVER, docs.get(0).getLine(4).getCoveredValue());
		assertEquals(2, docs.get(0).getLine(4).getBranchesToCover());
		assertEquals(0, docs.get(0).getLine(4).getCoveredBranches());
	}

	@Test
	void whenUnknownLineCoveredValue_testExceptionsAreThrown() {
		// arrange
		String sapl = "policy \"policy1\" \npermit\nwhere\ntrue;var id=1;";
		Collection<SaplDocument> documents = List
				.of(new SaplDocument(Paths.get("test.sapl"), 4, this.INTERPRETER.parse(sapl)));
		PolicyHit policyHit = new PolicyHit("", "policy1");
		CoverageTargets hits = new CoverageTargets(List.of(), List.of(policyHit), List.of());

		try (MockedStatic<LineCoveredValue> x = mockStatic(LineCoveredValue.class)) {
			LineCoveredValue badApple = mock(LineCoveredValue.class);
			when(badApple.ordinal()).thenReturn(4);
			when(LineCoveredValue.values()).thenReturn(new LineCoveredValue[] { LineCoveredValue.FULLY,
					LineCoveredValue.PARTLY, LineCoveredValue.NEVER, LineCoveredValue.IRRELEVANT, badApple });

			try (MockedConstruction<SaplDocumentLineCoverageInformation> mocked = Mockito
					.mockConstruction(SaplDocumentLineCoverageInformation.class, (mock, context) -> when(mock.getCoveredValue()).thenReturn(badApple))) {

				assertThrows(SaplTestException.class, () -> reporter.calcDocumentCoverage(documents, hits));
			}
		}
	}

	@Test
	void test_markLinesOfPolicyStatement_UnknownStatementType() {
		var mockStatement = Mockito.mock(Statement.class);
		EClass unknownNewStatementType = SaplPackage.Literals.ENTITLEMENT;
		Mockito.when(mockStatement.eClass()).thenReturn(unknownNewStatementType);
		try (MockedStatic<NodeModelUtils> nodeModelUtilsMockedStatic = mockStatic(NodeModelUtils.class)) {
			nodeModelUtilsMockedStatic.when(() -> NodeModelUtils.getNode(Mockito.any())).thenReturn(null);
			assertThrows(SaplTestException.class, () -> this.reporter.markLinesOfPolicyStatement("set", "policy", 0,
					mockStatement, true, null, null));
		}
	}

}
