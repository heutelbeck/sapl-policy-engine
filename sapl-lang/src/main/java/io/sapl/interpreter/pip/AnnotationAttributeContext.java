/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.pip;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * This Class holds the different attribute finders and PIPs as a context during
 * evaluation.
 */
@NoArgsConstructor
public class AnnotationAttributeContext implements AttributeContext {

	private static final String NO_POLICY_INFORMATION_POINT_ANNOTATION = "Provided class has no @PolicyInformationPoint annotation.";

	private static final String UNKNOWN_ATTRIBUTE = "Unknown attribute %s";

	private static final String RETURN_TYPE_MUST_BE_FLUX_OF_VALUES = "The return type of an attribute finder must be Flux<Val>. Was: %s";

	private final Map<String, Set<String>> attributeNamesByPipName = new HashMap<>();

	private final Map<String, Collection<AttributeFinderMetadata>> attributeMetadataByAttributeName = new HashMap<>();

	private final Collection<PolicyInformationPointDocumentation> pipDocumentations = new LinkedList<>();

	private List<String> functionsCache;

	private List<String> templatesCacheEnvironment;

	private List<String> templatesCache;

	/**
	 * Create the attribute context from a list of PIPs
	 * 
	 * @param policyInformationPoints a list of PIPs
	 * @throws InitializationException when loading the PIPs fails
	 */
	public AnnotationAttributeContext(Object... policyInformationPoints) throws InitializationException {
		for (Object pip : policyInformationPoints) {
			loadPolicyInformationPoint(pip);
		}
	}

	@Override
	public Flux<Val> evaluateAttribute(String attributeName, Val leftHandValue, Arguments arguments,
			Map<String, JsonNode> variables) {
		var attributeMetadata = lookupAttribute(attributeName, numberOfArguments(arguments), false);
		if (attributeMetadata == null)
			return Flux.just(Val.error(UNKNOWN_ATTRIBUTE, attributeName));
		return evaluateAttribute(attributeName, attributeMetadata, leftHandValue, arguments, variables);
	}

	@Override
	public Flux<Val> evaluateEnvironmentAttribute(String attributeName, Arguments arguments,
			Map<String, JsonNode> variables) {
		var attributeMetadata = lookupAttribute(attributeName, numberOfArguments(arguments), true);
		if (attributeMetadata == null)
			return Flux.just(Val.error(UNKNOWN_ATTRIBUTE, attributeName));
		return evaluateEnvironmentAttribute(attributeName, attributeMetadata, arguments, variables);
	}

	private Flux<Val> evaluateEnvironmentAttribute(String attributeName, AttributeFinderMetadata attributeMetadata,
			Arguments arguments, Map<String, JsonNode> variables) {
		var pip    = attributeMetadata.getPolicyInformationPoint();
		var method = attributeMetadata.getFunction();
		return attributeFinderArguments(attributeMetadata, arguments, variables)
				.switchMap(invokeAttributeFinderMethod(attributeName, attributeMetadata, pip, method));
	}

	private AttributeFinderMetadata lookupAttribute(String attributeName, int numberOfParameters,
			boolean environmentAttribute) {
		var nameMatches = attributeMetadataByAttributeName.get(attributeName);
		if (nameMatches == null)
			return null;
		AttributeFinderMetadata varArgsMatch = null;
		for (var candidate : nameMatches) {
			if (candidate.environmentAttribute != environmentAttribute)
				continue;
			if (candidate.varArgsParameters)
				varArgsMatch = candidate;
			else if (candidate.numberOfParameters == numberOfParameters)
				return candidate;
		}
		return varArgsMatch;
	}

	private Flux<Val> evaluateAttribute(String attributeName, AttributeFinderMetadata attributeMetadata,
			Val leftHandValue, Arguments arguments, Map<String, JsonNode> variables) {

		var pip    = attributeMetadata.getPolicyInformationPoint();
		var method = attributeMetadata.getFunction();

		return attributeFinderArguments(attributeMetadata, leftHandValue, arguments, variables)
				.switchMap(invokeAttributeFinderMethod(attributeName, attributeMetadata, pip, method));
	}

	@SuppressWarnings("unchecked")
	private Function<Object[], Publisher<? extends Val>> invokeAttributeFinderMethod(String attributeName,
			AttributeFinderMetadata attributeMetadata, Object pip, Method method) {
		return invocationParameters -> {
			try {
				return ((Flux<Val>) method.invoke(pip, invocationParameters)).map(val -> {
					var trace = new HashMap<String, Val>();
					trace.put("attribute", Val.of(attributeName));
					for (int i = 0; i < invocationParameters.length; i++) {
						if (invocationParameters[i] instanceof Val)
							trace.put("argument[" + i + "]", (Val) (invocationParameters[i]));
						if (invocationParameters[i] instanceof Map) {
							trace.put("argument[" + i + "]", Val.of("VARIABLES OMITTED"));
						}
					}
					trace.put("timestamp", Val.of(Instant.now().toString()));
					return val.withTrace(AttributeContext.class, trace);
				});
			} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
				if (e.getCause() != null)
					return Val.errorFlux(e.getCause().getMessage());
				return Val.errorFlux(e.getMessage());
			}
		};
	}

	private List<Flux<Val>> validatedArguments(AttributeFinderMetadata attributeMetadata, Arguments arguments) {
		var argumentFluxes                   = new ArrayList<Flux<Val>>(arguments.getArgs().size());
		var indexOfArgumentParameterOfMethod = 0;
		if (!attributeMetadata.isEnvironmentAttribute())
			indexOfArgumentParameterOfMethod++; // skip leftHand
		if (attributeMetadata.isAttributeWithVariableParameter())
			indexOfArgumentParameterOfMethod++; // skip variablesMap

		for (var argument : arguments.getArgs()) {
			Parameter parameter;
			if (attributeMetadata.isVarArgsParameters()) {
				parameter = attributeMetadata.function.getParameters()[indexOfArgumentParameterOfMethod];
			} else {
				parameter = attributeMetadata.function.getParameters()[indexOfArgumentParameterOfMethod++];
			}
			argumentFluxes.add(ParameterTypeValidator.validateType(argument.evaluate(), parameter));
		}
		return argumentFluxes;
	}

	private Flux<Object[]> attributeFinderArguments(AttributeFinderMetadata attributeMetadata, Arguments arguments,
			Map<String, JsonNode> variables) {

		var numberOfInvokationParameters = numberOfInvokationParametersForAttribute(attributeMetadata, arguments);

		if (arguments == null) {
			var invocationArguments = new Object[numberOfInvokationParameters];
			var argumentIndex       = 0;
			if (attributeMetadata.isAttributeWithVariableParameter())
				invocationArguments[argumentIndex++] = variables;
			if (attributeMetadata.isVarArgsParameters())
				invocationArguments[argumentIndex++] = new Val[0];
			return Flux.<Object[]>just(invocationArguments);
		}
		var argumentFluxes = validatedArguments(attributeMetadata, arguments);

		var combined = Flux.<Val, Object[]>combineLatest(argumentFluxes, argumentValues -> {
			var invocationArguments = new Object[numberOfInvokationParameters];
			var argumentIndex       = 0;
			if (attributeMetadata.isAttributeWithVariableParameter())
				invocationArguments[argumentIndex++] = variables;

			if (attributeMetadata.varArgsParameters) {
				var varArgsParameter = new Val[argumentValues.length];
				for (var i = 0; i < argumentValues.length; i++) {
					varArgsParameter[i] = (Val) argumentValues[i];
				}
				invocationArguments[argumentIndex] = varArgsParameter;
			} else {
				for (var valueIndex = 0; argumentIndex < numberOfInvokationParameters;) {
					invocationArguments[argumentIndex++] = argumentValues[valueIndex++];
				}
			}

			return invocationArguments;
		});
		return combined;
	}

	private Flux<Object[]> attributeFinderArguments(AttributeFinderMetadata attributeMetadata, Val leftHandValue,
			Arguments arguments, Map<String, JsonNode> variables) {

		var numberOfInvokationParameters = numberOfInvokationParametersForAttribute(attributeMetadata, arguments);

		if (arguments == null) {
			var invocationArguments = new Object[numberOfInvokationParameters];
			var argumentIndex       = 0;
			invocationArguments[argumentIndex++] = leftHandValue;
			if (attributeMetadata.isAttributeWithVariableParameter())
				invocationArguments[argumentIndex++] = variables;
			if (attributeMetadata.isVarArgsParameters())
				invocationArguments[argumentIndex++] = new Val[0];
			return Flux.<Object[]>just(invocationArguments);
		}

		var argumentFluxes = validatedArguments(attributeMetadata, arguments);

		var combined = Flux.<Val, Object[]>combineLatest(argumentFluxes, argumentValues -> {
			var invocationArguments = new Object[numberOfInvokationParameters];
			var argumentIndex       = 0;
			invocationArguments[argumentIndex++] = leftHandValue;

			if (attributeMetadata.isAttributeWithVariableParameter())
				invocationArguments[argumentIndex++] = variables;

			if (attributeMetadata.varArgsParameters) {
				var varArgsParameter = new Val[argumentValues.length];
				for (var i = 0; i < argumentValues.length; i++) {
					varArgsParameter[i] = (Val) argumentValues[i];
				}
				invocationArguments[argumentIndex] = varArgsParameter;
			} else {
				for (var valueIndex = 0; argumentIndex < numberOfInvokationParameters;) {
					invocationArguments[argumentIndex++] = argumentValues[valueIndex++];
				}
			}

			return invocationArguments;
		});
		return combined;
	}

	private int numberOfInvokationParametersForAttribute(AttributeFinderMetadata attributeMetadata,
			Arguments arguments) {

		var numberOfArguments = 0;

		if (!attributeMetadata.environmentAttribute)
			numberOfArguments++;

		if (attributeMetadata.isAttributeWithVariableParameter())
			numberOfArguments++;

		if (attributeMetadata.isVarArgsParameters()) {
			numberOfArguments++;
		} else {
			numberOfArguments += numberOfArguments(arguments);
		}

		return numberOfArguments;
	}

	private int numberOfArguments(Arguments arguments) {
		return arguments == null ? 0 : arguments.getArgs().size();
	}

	/**
	 * Makes attributes supplied by an object available to the policy engine.
	 * 
	 * @param pip The object implementing the Policy Information Point
	 * @throws InitializationException is thrown when the validation of the
	 *                                 annotation and method signatures finds
	 *                                 inconsistencies.
	 */
	public final void loadPolicyInformationPoint(Object pip) throws InitializationException {
		
		var clazz         = pip.getClass();
		var pipAnnotation = clazz.getAnnotation(PolicyInformationPoint.class);

		if (pipAnnotation == null)
			throw new InitializationException(NO_POLICY_INFORMATION_POINT_ANNOTATION);

		var pipName = pipAnnotation.name();
		if (pipName.isBlank())
			pipName = clazz.getSimpleName();

		if (attributeNamesByPipName.containsKey(pipName))
			throw new InitializationException("A PIP with the name '" + pipName + "' has already been registered.");

		attributeNamesByPipName.put(pipName, new HashSet<>());
		var pipDocumentation = new PolicyInformationPointDocumentation(pipName, pipAnnotation.description(), pip);
		pipDocumentation.setName(pipName);
		pipDocumentations.add(pipDocumentation);

		var foundAtLeastOneSuppliedAttributeInPip = false;
		for (Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Attribute.class)) {
				foundAtLeastOneSuppliedAttributeInPip = true;
				var annotation = method.getAnnotation(Attribute.class);
				importAttribute(pip, pipName, pipDocumentation, method, false, annotation.name(), annotation.docs());
			}
			if (method.isAnnotationPresent(EnvironmentAttribute.class)) {
				foundAtLeastOneSuppliedAttributeInPip = true;
				var annotation = method.getAnnotation(EnvironmentAttribute.class);
				importAttribute(pip, pipName, pipDocumentation, method, true, annotation.name(), annotation.docs());
			}
		}

		if (!foundAtLeastOneSuppliedAttributeInPip)
			throw new InitializationException("The PIP with the name '" + pipName
					+ "' does not declare any attributes. To declare an attribute, annotate a method with @Attribute.");

	}

	private void importAttribute(Object policyInformationPoint, String pipName,
			PolicyInformationPointDocumentation pipDocumentation, Method method, boolean isEnvironemtntAttribute,
			String attributeName, String documentation) throws InitializationException {

		if (attributeName.isBlank())
			attributeName = method.getName();

		var metadata        = metadataOf(policyInformationPoint, method, pipName, attributeName,
				isEnvironemtntAttribute);
		var name            = metadata.fullyQualifiedName();
		var namedAttributes = attributeMetadataByAttributeName.computeIfAbsent(name, k -> new ArrayList<>());
		assertNoNameCollision(namedAttributes, metadata);
		namedAttributes.add(metadata);
		attributeNamesByPipName.get(pipName).add(attributeName);
		pipDocumentation.documentation.put(metadata.getDocumentationCodeTemplate(), documentation);
	}

	private void assertNoNameCollision(Collection<AttributeFinderMetadata> attributesWithName,
			AttributeFinderMetadata newAttribute) throws InitializationException {
		for (var existingAttribute : attributesWithName)
			assertNoNameCollision(newAttribute, existingAttribute);
	}

	private void assertNoNameCollision(AttributeFinderMetadata newAttribute, AttributeFinderMetadata existingAttribute)
			throws InitializationException {
		if (existingAttribute.environmentAttribute == newAttribute.environmentAttribute
				&& (existingAttribute.varArgsParameters && newAttribute.varArgsParameters
						|| existingAttribute.numberOfParameters == newAttribute.numberOfParameters))
			throw new InitializationException("Cannot initialize PIPs. Attribute " + newAttribute.getLibraryName()
					+ " has multiple definitions which the PDP is not able not be able to disambiguate both at runtime.");
	}

	private AttributeFinderMetadata metadataOf(Object policyInformationPoint, Method method, String pipName,
			String attributeName, boolean isEnvironmentAttribute) throws InitializationException {

		assertValidReturnType(method);

		var parameterCount           = method.getParameterCount();
		var parameterUnderInspection = 0;

		if (!isEnvironmentAttribute) {
			assertFirstParameterIsVal(method);
			parameterUnderInspection++;
		}

		var requiresVariables = false;
		if (parameterUnderInspection < parameterCount && parameterTypeIsVariableMap(method, parameterUnderInspection)) {
			requiresVariables = true;
			parameterUnderInspection++;
		}

		if (parameterUnderInspection < parameterCount && parameterTypeIsArrayOfVal(method, parameterUnderInspection)) {
			if (parameterUnderInspection + 1 == parameterCount)
				return new AttributeFinderMetadata(policyInformationPoint, method, pipName, attributeName,
						isEnvironmentAttribute, requiresVariables, true, 0);
			else
				throw new InitializationException("The method " + method.getName()
						+ " has an array of Val as a parameter, which indicates a variable number of arguments."
						+ " However the array is followed by some other parameters. This is prohibited."
						+ " The array must be the last parameter of the attribute declaration.");
		}

		var numberOfInnerAttributeParameters = 0;
		for (; parameterUnderInspection < parameterCount; parameterUnderInspection++) {
			if (parameterTypeIsVal(method, parameterUnderInspection)) {
				numberOfInnerAttributeParameters++;
			} else {
				throw new InitializationException(
						"The method " + method.getName() + " declared a non Val as a parameter");
			}
		}
		return new AttributeFinderMetadata(policyInformationPoint, method, pipName, attributeName,
				isEnvironmentAttribute, requiresVariables, false, numberOfInnerAttributeParameters);
	}

	private void assertFirstParameterIsVal(Method method) throws InitializationException {
		if (method.getParameterCount() == 0)
			throw new InitializationException("Argument missing. First parameter of the method " + method.getName()
					+ " must be a Val for taking in the left-hand argument, but no argument was present.");
		if (!parameterTypeIsVal(method, 0))
			throw new InitializationException("First parameter of the method " + method.getName()
					+ " has an unexpected type. Was expecting a Val but got "
					+ method.getParameters()[0].getType().getSimpleName());
	}

	private void assertValidReturnType(Method method) throws InitializationException {
		var returnType        = method.getReturnType();
		var genericReturnType = method.getGenericReturnType();
		if (!(genericReturnType instanceof ParameterizedType))
			throw new InitializationException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES, returnType.getName());

		var returnTypeArgument = (Class<?>) ((ParameterizedType) genericReturnType).getActualTypeArguments()[0];
		if (!Flux.class.isAssignableFrom(returnType) || !Val.class.isAssignableFrom(returnTypeArgument)) {
			throw new InitializationException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES,
					returnType.getName() + '<' + returnTypeArgument.getName() + '>');
		}
	}

	private boolean parameterTypeIsVal(Method method, int indexOfParameter) {
		return isVal(method.getParameterTypes()[indexOfParameter]);
	}

	private boolean parameterTypeIsArrayOfVal(Method method, int indexOfParameter) {
		var parameterType = method.getParameterTypes()[indexOfParameter];
		if (!parameterType.isArray())
			return false;
		return isVal(parameterType.getComponentType());
	}

	private boolean isVal(Class<?> clazz) {
		return Val.class.isAssignableFrom(clazz);
	}

	private boolean parameterTypeIsVariableMap(Method method, int indexOfParameter) {
		var parameterTypes = method.getParameterTypes();
		var genericTypes   = method.getGenericParameterTypes();
		if (!Map.class.isAssignableFrom(parameterTypes[indexOfParameter]))
			return false;
		var firstTypeArgument  = (Class<?>) ((ParameterizedType) genericTypes[indexOfParameter])
				.getActualTypeArguments()[0];
		var secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[indexOfParameter])
				.getActualTypeArguments()[1];
		return String.class.isAssignableFrom(firstTypeArgument) && JsonNode.class.isAssignableFrom(secondTypeArgument);
	}

	@Override
	public Boolean isProvidedFunction(String attribute) {
		return attributeMetadataByAttributeName.containsKey(attribute);
	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		return Collections.unmodifiableCollection(pipDocumentations);
	}

	@Override
	public Collection<String> providedFunctionsOfLibrary(String pipName) {
		Collection<String> pips = attributeNamesByPipName.get(pipName);
		if (pips != null)
			return pips;
		else
			return new HashSet<>();
	}

	@Override
	public Collection<String> getAvailableLibraries() {
		return attributeNamesByPipName.keySet();
	}

	@Override
	public List<String> getEnvironmentAttributeCodeTemplates() {
		if (templatesCacheEnvironment == null) {
			var templates = new LinkedList<String>();
			for (var entry : attributeMetadataByAttributeName.entrySet())
				for (var attribute : entry.getValue())
					if (attribute.environmentAttribute)
						templates.add(attribute.getCodeTemplate());
			Collections.sort(templates);
			templatesCacheEnvironment = Collections.unmodifiableList(templates);
		}
		return templatesCacheEnvironment;
	}

	@Override
	public List<String> getAttributeCodeTemplates() {
		if (templatesCache == null) {
			var templates = new LinkedList<String>();
			for (var entry : attributeMetadataByAttributeName.entrySet())
				for (var attribute : entry.getValue())
					if (!attribute.environmentAttribute)
						templates.add(attribute.getCodeTemplate());
			Collections.sort(templates);
			templatesCache = Collections.unmodifiableList(templates);
		}
		return templatesCache;
	}

	@Override
	public Collection<String> getAllFullyQualifiedFunctions() {
		if (functionsCache == null) {
			var templates = new LinkedList<String>();
			for (var entry : attributeMetadataByAttributeName.entrySet())
				for (var attribute : entry.getValue())
					templates.add(attribute.fullyQualifiedName());
			Collections.sort(templates);
			functionsCache = Collections.unmodifiableList(templates);
		}
		return functionsCache;
	}

	@Override
	public Map<String, String> getDocumentedAttributeCodeTemplates() {
		var documentedAttributeCodeTemplates = new HashMap<String, String>();

		for (var entry : attributeMetadataByAttributeName.entrySet()) {
			for (var attribute : entry.getValue()) {
				var attributeCodeTemplate = attribute.getDocumentationCodeTemplate();
				for (var doc : pipDocumentations) {
					if (!documentedAttributeCodeTemplates.containsKey(doc.name)) {
						documentedAttributeCodeTemplates.put(doc.name, doc.description);
					}
					var documentationForCodeTemplate = doc.getDocumentation().get(attributeCodeTemplate);
					if (documentationForCodeTemplate != null) {
						documentedAttributeCodeTemplates.put(attribute.fullyQualifiedName(),
								documentationForCodeTemplate);
					}
				}
			}
		}
		return documentedAttributeCodeTemplates;
	}

}
