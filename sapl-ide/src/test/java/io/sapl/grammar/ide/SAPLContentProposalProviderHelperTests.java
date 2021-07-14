package io.sapl.grammar.ide;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Collection;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;

public class SAPLContentProposalProviderHelperTests {

	private static LibraryAttributeFinder attributeFinder;

	@BeforeAll
	public static void SetupLibraryAttributeFinder() throws InitializationException {
		AnnotationAttributeContext attributeContext = new AnnotationAttributeContext();
		attributeContext.loadPolicyInformationPoint(new ClockPolicyInformationPoint());

		EvaluationContext evaluationContext = new EvaluationContext(attributeContext, new AnnotationFunctionContext(),
				new HashMap<>());

		attributeFinder = new DefaultLibraryAttributeFinder(evaluationContext);
	}

	@Test
	public void createImportProposals_AtTheBeginningImportStatement_ReturnsLibrary() {
		String policyText = "import ";
		Collection<String> importProposals = SAPLContentProposalProviderHelper.createImportProposals("libsteps",
				policyText, policyText.length(), attributeFinder);
		assertAll(() -> assertThat(importProposals.size(), is(1)),
				() -> assertThat(importProposals.contains("clock"), is(true)));
	}

	@Test
	public void createImportProposals_WithPartialLibrary_ReturnsLibrary() {
		String policyText = "import cl";
		Collection<String> importProposals = SAPLContentProposalProviderHelper.createImportProposals("libsteps",
				policyText, policyText.length(), attributeFinder);
		assertAll(() -> assertThat(importProposals.size(), is(1)),
				() -> assertThat(importProposals.contains("clock"), is(true))

		);
	}

	@Test
	public void createImportProposals_WithFullLibrary_ReturnsFunction() {
		String policyText = "import clock.";
		Collection<String> importProposals = SAPLContentProposalProviderHelper.createImportProposals("libsteps",
				policyText, policyText.length(), attributeFinder);
		assertThat(importProposals.contains("now"), is(true));
	}

	@Test
	public void createImportProposals_WithFullLibraryAndPartialFunction_ReturnsFunction() {
		String policyText = "import clock.n";
		Collection<String> importProposals = SAPLContentProposalProviderHelper.createImportProposals("libsteps",
				policyText, policyText.length(), attributeFinder);
		assertThat(importProposals.contains("now"), is(true));

	}

	@Test
	public void createImportProposals_WithFullLibraryAndPartialFunctionAndNewLinesInBetween_ReturnsFunction() {
		String policyText = "import\nclock.\nn";
		Collection<String> importProposals = SAPLContentProposalProviderHelper.createImportProposals("libsteps",
				policyText, policyText.length(), attributeFinder);
		assertThat(importProposals.contains("now"), is(true));

	}

	@Test
	public void createImportProposals_WithPrecedingTextAndFullLibraryAndPartialFunction_ReturnsFunction() {
		String policyText = "import clock.yesterday\nimport clock.n";
		Collection<String> importProposals = SAPLContentProposalProviderHelper.createImportProposals("libsteps",
				policyText, policyText.length(), attributeFinder);
		assertThat(importProposals.contains("now"), is(true));
	}

	@Test
	public void createImportProposals_WithPrecedingAndSucceedingAndFullLibraryAndPartialFunction_ReturnsFunction() {
		String policyText = "import clock.yesterday\nimport clock.n policy \"test policy\" deny";
		Collection<String> importProposals = SAPLContentProposalProviderHelper.createImportProposals("libsteps",
				policyText, 38, attributeFinder);
		assertThat(importProposals.contains("now"), is(true));
	}
}
