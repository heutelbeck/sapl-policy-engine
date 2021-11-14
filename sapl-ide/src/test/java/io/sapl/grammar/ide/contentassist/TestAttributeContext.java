package io.sapl.grammar.ide.contentassist;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import reactor.core.publisher.Flux;

public class TestAttributeContext implements AttributeContext {

	private final Map<String, Set<String>> availableLibraries;

	public TestAttributeContext() {
		availableLibraries = new HashMap<>();
		availableLibraries.put("clock", Set.of("now", "millis", "ticker"));
	}

	@Override
	public Boolean isProvidedFunction(String function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Collection<String> providedFunctionsOfLibrary(String pipName) {
		return availableLibraries.getOrDefault(pipName, new HashSet<>());
	}

	@Override
	public Collection<String> getAvailableLibraries() {
		return availableLibraries.keySet();
	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Flux<Val> evaluateAttribute(String attribute, Val value, EvaluationContext ctx, Arguments arguments) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Flux<Val> evaluateEnvironmentAttribute(String attribute, EvaluationContext ctx, Arguments arguments) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<String> getCodeTemplatesWithPrefix(String prefix, boolean isEnvirionmentAttribute) {
		return List.of("clock.now", "clock.millis", "clock.ticker");
	}

	@Override
	public Collection<String> getAllFullyQualifiedFunctions() {
		return List.of("clock.now", "clock.millis", "clock.ticker");
	}

}
