package io.sapl.grammar.ide;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;

public class DefaultLibraryAttributeFinderTests {

	private static DefaultLibraryAttributeFinder attributeFinder;

	@BeforeAll
	public static void SetupLibraryAttributeFinder() throws InitializationException {
		AnnotationAttributeContext attributeContext = new AnnotationAttributeContext();
		attributeContext.loadPolicyInformationPoint(new ClockPolicyInformationPoint());

		AnnotationFunctionContext funtionContext = new AnnotationFunctionContext();
		funtionContext.loadLibrary(new FilterFunctionLibrary());
		funtionContext.loadLibrary(new StandardFunctionLibrary());
		funtionContext.loadLibrary(new TemporalFunctionLibrary());

		Map<String, JsonNode> environmentVariables = new HashMap<>();
		EvaluationContext evaluationContext = new EvaluationContext(attributeContext, funtionContext,
				environmentVariables);

		attributeFinder = new DefaultLibraryAttributeFinder(evaluationContext);
	}

	@ParameterizedTest
	@ValueSource(strings = { "time", "filter", "standard", "clock" })
	public void getAvailableAttributes_WithEmptyString_ReturnsAllLibraries(String value) {
		Collection<String> availableAttributes = attributeFinder.GetAvailableAttributes("");
		assertThat(availableAttributes.contains(value), is(true));
	}

	@ParameterizedTest
	@CsvSource({ "time, ti", "clock, clo", "filter, filt", "standard, stand" })
	public void getAvailableAttributes_WithNeedle_ReturnsMatchingLibaries(ArgumentsAccessor arguments) {
		String needle = arguments.getString(1);
		String expectedLibrary = arguments.getString(0);

		Collection<String> availableAttributes = attributeFinder.GetAvailableAttributes(needle);

		assertAll(() -> assertThat(availableAttributes.size(), is(1)),
				() -> assertThat(availableAttributes.contains(expectedLibrary), is(true)));
	}

	@ParameterizedTest
	@CsvSource({ "clock., now", "clock., ticker" })
	public void getAvailableAttributes_WithLibraryQualifier_ReturnsAllFunctions(ArgumentsAccessor arguments) {
		String needle = arguments.getString(0);
		String expectedFunction = arguments.getString(1);

		Collection<String> availableAttributes = attributeFinder.GetAvailableAttributes(needle);

		assertThat(availableAttributes.contains(expectedFunction), is(true));
	}

	@ParameterizedTest
	@CsvSource({ "clock.n, now", "clock.tic, ticker" })
	public void getAvailableAttributes_WithLibraryQualifierAndFunctionNeedle_ReturnsMatchingFunctions(
			ArgumentsAccessor arguments) {
		String needle = arguments.getString(0);
		String expectedFunction = arguments.getString(1);

		Collection<String> availableAttributes = attributeFinder.GetAvailableAttributes(needle);

		assertThat(availableAttributes.contains(expectedFunction), is(true));
	}
}
