/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * This Class holds the different attribute finders and PIPs as a context during
 * evaluation.
 */
@NoArgsConstructor
public class AnnotationAttributeContext implements AttributeContext {

	private static final String CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION = "Provided class has no @PolicyInformationPoint annotation.";

	private static final String UNKNOWN_ATTRIBUTE = "Unknown attribute %s";

	private static final String RETURN_TYPE_MUST_BE_FLUX_OF_VALUES = "The return type of an attribute finder must be Flux<Val>. Was: %s";

	private final Map<String, Set<String>> attributeNamesByPipName = new HashMap<>();

	private final Map<String, Collection<AttributeFinderMetadata>> attributeMetadataByAttributeName = new HashMap<>();

	private final Collection<PolicyInformationPointDocumentation> pipDocumentations = new LinkedList<>();

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
	public Flux<Val> evaluateEnvironmentAttribute(String attributeName, EvaluationContext ctx, Arguments arguments) {
		int numberOfParameters = numberOfArguments(arguments);
		var attributeMetadata = lookupAttribute(attributeName, numberOfParameters, true);

		if (attributeMetadata == null)
			return Flux.just(Val.error(UNKNOWN_ATTRIBUTE, attributeName));

		try {
			return evaluateEnvironmentAttribute(attributeMetadata, ctx, arguments);
		} catch (Throwable e) {
			return Flux.just(Val.error("Failed to evaluate attribute", new PolicyEvaluationException(e)));
		}
	}

	@SuppressWarnings("unchecked")
	private Flux<Val> evaluateEnvironmentAttribute(AttributeFinderMetadata attributeMetadata, EvaluationContext ctx,
			Arguments arguments) throws IllegalAccessException, InvocationTargetException {
		var pip = attributeMetadata.getPolicyInformationPoint();
		var method = attributeMetadata.getFunction();

		var numberOfArgumentsForMethodInvocation = calculateSizeOfArgumentsArrayForInvocationExcludingLeftHand(
				attributeMetadata);

		if (numberOfArgumentsForMethodInvocation == 0)
			return (Flux<Val>) method.invoke(pip);

		var invocationArguments = constructArgumentArrayForInvocationWithoutLeftHand(attributeMetadata, ctx, arguments,
				numberOfArgumentsForMethodInvocation);

		return (Flux<Val>) method.invoke(pip, invocationArguments);

	}

	private Object[] constructArgumentArrayForInvocationWithoutLeftHand(AttributeFinderMetadata attributeMetadata,
			EvaluationContext ctx, Arguments arguments, int numberOfArgumentsForMethodInvocation) {
		var invocationArguments = new Object[numberOfArgumentsForMethodInvocation];

		var argumentIndex = 0;
		if (attributeMetadata.requiresVariables)
			invocationArguments[argumentIndex++] = ctx.getVariableCtx().getVariables();

		if (attributeMetadata.varArgsParameters) {
			invocationArguments[argumentIndex] = buildVarArgsArrayFromArguments(arguments, ctx, attributeMetadata,
					argumentIndex);
		} else {
			if (arguments != null) {
				for (Expression argument : arguments.getArgs()) {
					var parameter = attributeMetadata.function.getParameters()[argumentIndex];
					invocationArguments[argumentIndex++] = ParameterTypeValidator
							.validateType(argument.evaluate(ctx, Val.UNDEFINED), parameter);
				}
			}
		}
		return invocationArguments;
	}

	private int calculateSizeOfArgumentsArrayForInvocationExcludingLeftHand(AttributeFinderMetadata attributeMetadata) {
		var numberOfArgumentsForMethodInvocation = 0;
		if (attributeMetadata.requiresVariables)
			numberOfArgumentsForMethodInvocation++;

		if (attributeMetadata.varArgsParameters)
			numberOfArgumentsForMethodInvocation++;
		else
			numberOfArgumentsForMethodInvocation += attributeMetadata.getNumberOfParameters();
		return numberOfArgumentsForMethodInvocation;
	}

	private Flux<Val>[] buildVarArgsArrayFromArguments(Arguments arguments, EvaluationContext ctx,
			AttributeFinderMetadata attributeMetadata, int argumentIndex) {
		int numberOfParameters = numberOfArguments(arguments);

		@SuppressWarnings("unchecked")
		Flux<Val>[] varArgsArray = new Flux[numberOfParameters];

		var parameter = attributeMetadata.function.getParameters()[argumentIndex];

		var i = 0;
		if (arguments != null) {
			for (Expression argument : arguments.getArgs()) {
				varArgsArray[i++] = ParameterTypeValidator.validateType(argument.evaluate(ctx, Val.UNDEFINED),
						parameter);
			}
		}
		return varArgsArray;
	}

	private int numberOfArguments(Arguments arguments) {
		return arguments == null ? 0 : arguments.getArgs().size();
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

	@Override
	public Flux<Val> evaluateAttribute(String attributeName, Val leftHandValue, EvaluationContext ctx,
			Arguments arguments) {
		int numberOfParameters = numberOfArguments(arguments);
		var attributeMetadata = lookupAttribute(attributeName, numberOfParameters, false);

		if (attributeMetadata == null)
			return Flux.just(Val.error(UNKNOWN_ATTRIBUTE, attributeName));

		try {
			return evaluateAttribute(attributeMetadata, leftHandValue, ctx, arguments);
		} catch (Throwable e) {
			return Flux.just(Val.error("Failed to evaluate attribute", new PolicyEvaluationException(e)));
		}

	}

	@SuppressWarnings("unchecked")
	private Flux<Val> evaluateAttribute(AttributeFinderMetadata attributeMetadata, Val leftHandValue,
			EvaluationContext ctx, Arguments arguments) throws IllegalAccessException, InvocationTargetException {
		var pip = attributeMetadata.getPolicyInformationPoint();
		var method = attributeMetadata.getFunction();

		var numberOfArgumentsForMethodInvocation = calculateSizeOfArgumentsArrayForInvocationExcludingLeftHand(
				attributeMetadata) + 1;

		var invocationArguments = constructArgumentArrayForInvocationWithLeftHand(attributeMetadata, leftHandValue, ctx,
				arguments, numberOfArgumentsForMethodInvocation);

		return (Flux<Val>) method.invoke(pip, invocationArguments);

	}

	private Object[] constructArgumentArrayForInvocationWithLeftHand(AttributeFinderMetadata attributeMetadata,
			Val leftHandValue, EvaluationContext ctx, Arguments arguments, int numberOfArgumentsForMethodInvocation) {
		var invocationArguments = new Object[numberOfArgumentsForMethodInvocation];

		var argumentIndex = 0;
		invocationArguments[argumentIndex++] = leftHandValue;

		if (attributeMetadata.requiresVariables)
			invocationArguments[argumentIndex++] = ctx.getVariableCtx().getVariables();

		if (attributeMetadata.varArgsParameters) {
			invocationArguments[argumentIndex] = buildVarArgsArrayFromArguments(arguments, ctx, attributeMetadata,
					argumentIndex);
		} else {
			if (arguments != null) {
				for (Expression argument : arguments.getArgs()) {
					var parameter = attributeMetadata.function.getParameters()[argumentIndex];
					invocationArguments[argumentIndex++] = ParameterTypeValidator
							.validateType(argument.evaluate(ctx, Val.UNDEFINED), parameter);
				}
			}
		}
		return invocationArguments;
	}

	public final void loadPolicyInformationPoint(Object pip) throws InitializationException {
		final Class<?> clazz = pip.getClass();

		final PolicyInformationPoint pipAnnotation = clazz.getAnnotation(PolicyInformationPoint.class);

		if (pipAnnotation == null)
			throw new InitializationException(CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION);

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
				importAttribute(pip, pipName, pipDocumentation, method);
			}
		}

		if (!foundAtLeastOneSuppliedAttributeInPip)
			throw new InitializationException("The PIP with the name '" + pipName
					+ "' does not declare any attributes. To declare an attribute, annotate a method with @Attribute.");

	}

	private void importAttribute(Object policyInformationPoint, String pipName,
			PolicyInformationPointDocumentation pipDocs, Method method) throws InitializationException {

		var annotation = method.getAnnotation(Attribute.class);

		String attributeName = annotation.name();

		if (attributeName.isBlank())
			attributeName = method.getName();

		var metadata = metadataOf(policyInformationPoint, method, pipName, attributeName);
		var name = metadata.fullyQualifiedName();
		var attributesWithName = attributeMetadataByAttributeName.get(name);
		if (attributesWithName == null) {
			attributesWithName = new ArrayList<>();
			attributeMetadataByAttributeName.put(name, attributesWithName);
		}
		assertNoCollision(attributesWithName, metadata);
		attributesWithName.add(metadata);
		attributeNamesByPipName.get(pipName).add(attributeName);
		pipDocs.documentation.put(metadata.getDocumentationCodeTemplate(), annotation.docs());
	}

	private void assertNoCollision(Collection<AttributeFinderMetadata> attributesWithName,
			AttributeFinderMetadata newAttribute) throws InitializationException {
		for (var existingAttribute : attributesWithName)
			assertNoCollisiton(newAttribute, existingAttribute);
	}

	private void assertNoCollisiton(AttributeFinderMetadata newAttribute, AttributeFinderMetadata existingAttribute)
			throws InitializationException {
		if (existingAttribute.environmentAttribute == newAttribute.environmentAttribute

				&& (existingAttribute.varArgsParameters && newAttribute.varArgsParameters

						|| existingAttribute.numberOfParameters == newAttribute.numberOfParameters))
			throw new InitializationException("Cannot initialize PIPs. Attribute " + newAttribute.getLibraryName()
					+ " has multiple defienitions which the PDP is not able not be able to disabmiguate both at runtime.");
	}

	private AttributeFinderMetadata metadataOf(Object policyInformationPoint, Method method, String pipName,
			String attributeName) throws InitializationException {
		assertValidReturnType(method);

		var parameterCount = method.getParameterCount();

		if (parameterCount == 0)
			return new AttributeFinderMetadata(policyInformationPoint, method, pipName, attributeName, true, false,
					false, 0);

		var indexOfParameterInspect = 0;

		assertFirstParameterIsOneOfTheLegalTypesForTheFirstElement(method);

		var isEnvironmentAttribute = true;
		if (firstParameterIsAVal(method)) {
			isEnvironmentAttribute = false;
			indexOfParameterInspect++;
		}

		if (indexOfParameterInspect == parameterCount)
			return new AttributeFinderMetadata(policyInformationPoint, method, pipName, attributeName,
					isEnvironmentAttribute, false, false, 0);

		var requiresVariables = false;
		if (isVariableMap(method, indexOfParameterInspect)) {
			requiresVariables = true;
			indexOfParameterInspect++;
		}

		if (indexOfParameterInspect == parameterCount)
			return new AttributeFinderMetadata(policyInformationPoint, method, pipName, attributeName,
					isEnvironmentAttribute, requiresVariables, false, 0);

		if (isArrayOfFluxOfVal(method, indexOfParameterInspect)) {
			if (indexOfParameterInspect + 1 == parameterCount)
				return new AttributeFinderMetadata(policyInformationPoint, method, pipName, attributeName,
						isEnvironmentAttribute, requiresVariables, true, 0);
			else
				throw new InitializationException("The method " + method.getName()
						+ " has an array of Flux<Val> as a parameter, which indicates a variable number of arguments. However the array is followed by some other parameters. This is prohibited. The array must be the last parameter of the attribute declaration.");
		}

		var parameters = 0;
		for (; indexOfParameterInspect < parameterCount; indexOfParameterInspect++) {
			if (isFluxOfVal(method, indexOfParameterInspect)) {
				parameters++;
			} else {
				throw new InitializationException(
						"The method " + method.getName() + " declared a non Flux<Val> as a parameter");
			}
		}
		return new AttributeFinderMetadata(policyInformationPoint, method, pipName, attributeName,
				isEnvironmentAttribute, requiresVariables, false, parameters);
	}

	private void assertFirstParameterIsOneOfTheLegalTypesForTheFirstElement(Method method)
			throws InitializationException {
		if (firstParameterIsAVal(method) || isVariableMap(method, 0) || isFluxOfVal(method, 0)
				|| isArrayOfFluxOfVal(method, 0))
			return;

		throw new InitializationException("First parameter of the method " + method.getName()
				+ " has an unexpected type. Was expecting a Val, Map<String,JsonNode>, Flux<Val>, or Flux<Val>... but got "
				+ method.getParameters()[0].getType().getSimpleName());
	}

	private void assertValidReturnType(Method method) throws InitializationException {
		final Class<?> returnType = method.getReturnType();
		final Type genericReturnType = method.getGenericReturnType();
		if (!(genericReturnType instanceof ParameterizedType))
			throw new InitializationException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES, returnType.getName());

		final Class<?> returnTypeArgument = (Class<?>) ((ParameterizedType) genericReturnType)
				.getActualTypeArguments()[0];
		if (!Flux.class.isAssignableFrom(returnType) || !Val.class.isAssignableFrom(returnTypeArgument)) {
			throw new InitializationException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES,
					returnType.getName() + '<' + returnTypeArgument.getName() + '>');
		}
	}

	private boolean firstParameterIsAVal(Method method) {
		var parameterTypes = method.getParameterTypes();
		return Val.class.isAssignableFrom(parameterTypes[0]);
	}

	private boolean isVariableMap(Method method, int indexOfParameter) {
		var parameterTypes = method.getParameterTypes();
		var genericTypes = method.getGenericParameterTypes();
		if (!Map.class.isAssignableFrom(parameterTypes[indexOfParameter]))
			return false;
		var firstTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[indexOfParameter])
				.getActualTypeArguments()[0];
		var secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[indexOfParameter])
				.getActualTypeArguments()[1];
		return String.class.isAssignableFrom(firstTypeArgument) && JsonNode.class.isAssignableFrom(secondTypeArgument);
	}

	private boolean isFluxOfVal(Method method, int indexOfParameter) {
		var parameterTypes = method.getParameterTypes();
		var type = parameterTypes[indexOfParameter];
		if (!Flux.class.isAssignableFrom(type))
			return false;
		var genericTypes = method.getGenericParameterTypes();
		var firstTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[indexOfParameter])
				.getActualTypeArguments()[0];
		return Val.class.isAssignableFrom(firstTypeArgument);
	}

	private boolean isArrayOfFluxOfVal(Method method, int indexOfParameter) {
		var genericTypes = method.getGenericParameterTypes();
		var type = genericTypes[indexOfParameter];
		if (!GenericArrayType.class.isAssignableFrom(type.getClass()))
			return false;
		var genericArray = (GenericArrayType) type;
		var parameterizedType = (ParameterizedType) genericArray.getGenericComponentType();

		if (!Flux.class.isAssignableFrom((Class<?>) parameterizedType.getRawType()))
			return false;

		var firstTypeArgument = (Class<?>) parameterizedType.getActualTypeArguments()[0];
		return Val.class.isAssignableFrom(firstTypeArgument);
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

	/**
	 * Metadata for attribute finders.
	 */
	@Data
	@AllArgsConstructor
	public static class AttributeFinderMetadata implements LibraryEntryMetadata {

		Object policyInformationPoint;

		Method function;

		String libraryName;

		String functionName;

		boolean environmentAttribute;

		boolean requiresVariables;

		boolean varArgsParameters;

		int numberOfParameters;

		@Override
		public String getDocumentationCodeTemplate() {
			var sb = new StringBuilder();
			var indexOfParameterBeingDescribed = 0;

			if (!isEnvironmentAttribute())
				sb.append(describeParameterForDocumentation(indexOfParameterBeingDescribed++)).append('.');

			if (requiresVariables)
				indexOfParameterBeingDescribed++;

			sb.append('<').append(fullyQualifiedName());

			appendParameterList(sb, indexOfParameterBeingDescribed, this::describeParameterForDocumentation);

			sb.append('>');
			return sb.toString();
		}

		@Override
		public String getCodeTemplate() {
			var sb = new StringBuilder();
			var indexOfParameterBeingDescribed = 0;

			if (!isEnvironmentAttribute())
				indexOfParameterBeingDescribed++;

			if (requiresVariables)
				indexOfParameterBeingDescribed++;

			sb.append(fullyQualifiedName());

			appendParameterList(sb, indexOfParameterBeingDescribed, this::getParameterName);

			sb.append('>');
			return sb.toString();
		}

	}

	@Override
	public Collection<String> getAvailableLibraries() {
		return attributeNamesByPipName.keySet();
	}

	@Override
	public List<String> getCodeTemplatesWithPrefix(String prefix, boolean isEnvirionmentAttribute) {
		var templates = new LinkedList<String>();
		for (var entry : attributeMetadataByAttributeName.entrySet())
			for (var attribute : entry.getValue())
				if (attribute.environmentAttribute == isEnvirionmentAttribute
						&& attribute.fullyQualifiedName().startsWith(prefix))
					templates.add(attribute.getCodeTemplate());
		return templates;
	}

	@Override
	public Collection<String> getAllFullyQualifiedFunctions() {
		var templates = new LinkedList<String>();
		for (var entry : attributeMetadataByAttributeName.entrySet())
			for (var attribute : entry.getValue())
				templates.add(attribute.fullyQualifiedName());
		return templates;
	}
}
