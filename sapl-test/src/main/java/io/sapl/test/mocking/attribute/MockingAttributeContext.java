package io.sapl.test.mocking.attribute;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.mocking.attribute.models.AttributeParentValueMatcher;
import io.sapl.test.steps.NumberOfExpectSteps;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class MockingAttributeContext implements AttributeContext {

	private static final String ERROR_MOCK_INVALID_FULLNAME = "Got invalid attribute reference containing more than one \".\" delimiter: \"%s\"";
	private static final String ERROR_NOT_MARKED_DYNAMIC_MOCK = "No registered dynamic mock found for \"%s\". Did you forgot to register the mock via \".givenAttribute(\"%s\")\"";
	private static final String ERROR_DUPLICATE_MOCK_REGISTRATION = "Duplicate registration of mock for PIP attribute \"%s\"";
	private static final String ERROR_LOADING_PIP_NOT_SUPPORTED = "Loading a PIP on a MockingAttributeContext is not supported";
	private static final String NAME_DELIMITER = ".";
	/**
	 * Holds an AttributeContext implementation to delegate evaluations if this
	 * attribute is not mocked
	 */
	private final AttributeContext unmockedAttributeContext;
	/**
	 * Contains a Map of all registered mocks. Key is the String of the fullname of
	 * the attribute finder Value is the {@link Flux<Val>} to be returned
	 */
	private final Map<String, AttributeMock> registeredMocks;
	private final Map<String, PolicyInformationPointDocumentation> pipDocumentations;

	/**
	 * Constructor of MockingAttributeContext
	 * @param unmockedAttributeContext unmocked "normal" AttributeContext do delegate unmocked attribute calls
	 * @param numberOfExpectSteps {@link NumberOfExpectSteps} to convert infinite streams to finite ones via a .take(numberOfExpectSteps) call. "null" if no conversion to a finite stream should happen.
	 */
	public MockingAttributeContext(AttributeContext unmockedAttributeContext) {
		this.unmockedAttributeContext = unmockedAttributeContext;
		this.registeredMocks = new HashMap<String, AttributeMock>();
		this.pipDocumentations = new HashMap<>();
	}

	@Override
	public Boolean isProvidedFunction(String function) {
		if (this.registeredMocks.containsKey(function)) {
			log.trace("Attribute \"{}\" is mocked", function);
			return Boolean.TRUE;
		} else if (unmockedAttributeContext.isProvidedFunction(function)) {
			log.trace("Attribute \"{}\" is provided by unmocked attribute context", function);
			return Boolean.TRUE;
		} else {
			log.trace("Attribute \"{}\" is NOT provided", function);
			return Boolean.FALSE;
		}
	}

	@Override
	public Collection<String> providedFunctionsOfLibrary(String pipName) {
		Set<String> set = new HashSet<>();

		for (String fullName : this.registeredMocks.keySet()) {
			String[] splitted = fullName.split(Pattern.quote(NAME_DELIMITER));
			if (splitted[0].equals(pipName))
				set.add(splitted[1]);
		}

		set.addAll(this.unmockedAttributeContext.providedFunctionsOfLibrary(pipName));

		return set;
	}

	@Override
	public Flux<Val> evaluate(String attribute, Val value, EvaluationContext ctx, Arguments arguments) {
		AttributeMock mock = this.registeredMocks.get(attribute);
		if (mock != null) {
			log.debug("| | | | |-- Evaluate mocked attribute \"{}\"", attribute);
			
			Val parentValue = value;
			Map<String, JsonNode> variables = ctx.getVariableCtx().getVariables();
			List<Flux<Val>> args = new LinkedList<Flux<Val>>();
			if (arguments != null) {
				for (Expression argument : arguments.getArgs()) {
					args.add(argument.evaluate(ctx, Val.UNDEFINED));
				}
			}			
			
			return mock.evaluate(parentValue, variables, args).doOnNext((val) -> log.trace("| | | | |-- AttributeMock returned: " + val.toString()));
		} else {
			log.debug("| | | | |-- Delegate attribute \"{}\" to unmocked attribute context", attribute);
			return this.unmockedAttributeContext.evaluate(attribute, value, ctx, arguments);
		}
	}

	@Override
	public void loadPolicyInformationPoint(Object pip) throws InitializationException {
		throw new SaplTestException(ERROR_LOADING_PIP_NOT_SUPPORTED);
	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		Collection<PolicyInformationPointDocumentation> doc = new LinkedList<PolicyInformationPointDocumentation>(
				this.pipDocumentations.values());
		doc.addAll(this.unmockedAttributeContext.getDocumentation());
		return Collections.unmodifiableCollection(doc);
	}

	public void markAttributeMock(String fullname) {
		checkImportName(fullname);

		if (this.registeredMocks.containsKey(fullname)) {
			throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullname));
		} else {
			AttributeMockPublisher mock = new AttributeMockPublisher(fullname);
			this.registeredMocks.put(fullname, mock);

			addNewPIPDocumentation(fullname, mock);
		}
	}

	public void mockEmit(String fullname, Val returns) {
		AttributeMock mock = this.registeredMocks.get(fullname);
		
		if (mock instanceof AttributeMockPublisher) {
			((AttributeMockPublisher)mock).mockEmit(returns);
			return;
		} else {	
			throw new SaplTestException(String.format(ERROR_NOT_MARKED_DYNAMIC_MOCK, fullname, fullname));
		}
	}

	public void loadAttributeMockForParentValue(String fullname, AttributeParentValueMatcher parentValueMatcher, Val returns) {
		checkImportName(fullname);

		AttributeMock mock = this.registeredMocks.get(fullname);
		if (mock != null) {
			if(mock instanceof AttributeMockForParentValue) {
				((AttributeMockForParentValue) mock).loadMockForParentValue(parentValueMatcher, returns);
			} else {
				throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullname));				
			}
		} else {
			AttributeMockForParentValue newMock = new AttributeMockForParentValue(fullname);
			newMock.loadMockForParentValue(parentValueMatcher, returns);
			this.registeredMocks.put(fullname, newMock);

			addNewPIPDocumentation(fullname, newMock);
		}
	}
	
	public void loadAttributeMockForParentValueAndArguments(String fullname, AttributeParameters parameters, Val returns) {
		checkImportName(fullname);

		AttributeMock mock = this.registeredMocks.get(fullname);
		if (mock != null) {
			if(mock instanceof AttributeMockForParentValueAndArguments) {
				((AttributeMockForParentValueAndArguments) mock).loadMockForParentValueAndArguments(parameters, returns);
			} else {
				throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullname));				
			}
		} else {
			AttributeMockForParentValueAndArguments newMock = new AttributeMockForParentValueAndArguments(fullname);
			newMock.loadMockForParentValueAndArguments(parameters, returns);
			this.registeredMocks.put(fullname, newMock);

			addNewPIPDocumentation(fullname, newMock);
		}
	}
	
	public void loadAttributeMock(String fullname, Duration timing, Val... returns) {
		checkImportName(fullname);

		if (this.registeredMocks.containsKey(fullname)) {
			throw new SaplTestException(String.format(ERROR_DUPLICATE_MOCK_REGISTRATION, fullname));
		} else {
			AttributeMockTiming mock = new AttributeMockTiming(fullname);
			mock.loadAttributeMockWithTiming(timing, returns);
			this.registeredMocks.put(fullname, mock);

			addNewPIPDocumentation(fullname, mock);
		}
	}

	public void assertVerifications() {
		this.registeredMocks.forEach((fullname, mock) -> {
			mock.assertVerifications();
		});
	}

	private void checkImportName(String fullname) {
		String[] splitted = fullname.split(Pattern.quote(NAME_DELIMITER));
		if (splitted.length != 2) {
			throw new SaplTestException(String.format(ERROR_MOCK_INVALID_FULLNAME, fullname));
		}
	}

	private void addNewPIPDocumentation(String fullname, AttributeMock mock) {
		String[] splitted = fullname.split(Pattern.quote(NAME_DELIMITER));
		String pipName = splitted[0];
		String attributeName = splitted[1];

		var existingDoc = this.pipDocumentations.get(pipName);
		if (existingDoc != null) {
			existingDoc.getDocumentation().put(attributeName, "Mocked Attribute");
		} else {
			PolicyInformationPointDocumentation pipDocs = new PolicyInformationPointDocumentation(pipName,
					"Mocked PIP " + pipName, mock);
			pipDocs.getDocumentation().put(attributeName, "Mocked Attribute");
			this.pipDocumentations.put(pipName, pipDocs);
		}

	}

	@Override
	public Collection<String> getAvailableLibraries() {
		return registeredMocks.keySet();
	}

}
