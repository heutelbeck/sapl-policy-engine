/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.impl.Val;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.validation.IllegalParameterType;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

/**
 * This Class holds the different attribute finders and PIPs as a context during
 * evaluation.
 */
@Slf4j
@NoArgsConstructor
public class AnnotationAttributeContext implements AttributeContext {

	private static final int REQUIRED_NUMBER_OF_PARAMETERS = 2;

	private static final String NAME_DELIMITER = ".";

	private static final String ATTRIBUTE_NAME_COLLISION_PIP_CONTAINS_MULTIPLE_ATTRIBUTE_METHODS_WITH_NAME = "Attribute name collision. PIP contains multiple attribute methods with name %s";

	private static final String CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION = "Provided class has no @PolicyInformationPoint annotation.";

	private static final String UNKNOWN_ATTRIBUTE = "Unknown attribute %s";

	private static final String BAD_NUMBER_OF_PARAMETERS = "Bad number of parameters for attribute finder. Attribute finders are supposed to have at least one JsonNode and one Map<String, JsonNode> as parameters. The method had %d parameters";

	private static final String FIRST_PARAMETER_OF_METHOD_MUST_BE_A_VALUE = "First parameter of method must be a Value. Was: %s";

	private static final String ADDITIONAL_PARAMETER_OF_METHOD_MUST_BE_A_FLUX_OF_VALUES = "Additional parameters of the method must be Flux<Val>. Was: %s.";

	private static final String SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP = "Second parameter of method must be a Map<String, JsonNode>. Was: %s";

	private static final String RETURN_TYPE_MUST_BE_FLUX_OF_VALUES = "The return type of an attribute finder must be Flux<Val>. Was: %s";

	private Map<String, Collection<String>> attributeNamesByPipName = new HashMap<>();

	private Map<String, AttributeFinderMetadata> attributeMetadataByAttributeName = new HashMap<>();

	private Collection<PolicyInformationPointDocumentation> pipDocumentations = new LinkedList<>();

	/**
	 * Create the attribute context from a list of PIPs
	 * @param policyInformationPoints a list of PIPs
	 * @throws AttributeException when loading the PIPs fails
	 */
	public AnnotationAttributeContext(Object... policyInformationPoints) throws AttributeException {
		for (Object pip : policyInformationPoints) {
			loadPolicyInformationPoint(pip);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Flux<Val> evaluate(String attribute, Val value, EvaluationContext ctx, Arguments arguments) {
		final AttributeFinderMetadata metadata = attributeMetadataByAttributeName.get(attribute);
		if (metadata == null) {
			return Flux.error(new AttributeException(UNKNOWN_ATTRIBUTE, attribute));
		}

		final Object pip = metadata.getPolicyInformationPoint();
		final Method method = metadata.getFunction();
		final Parameter firstParameter = method.getParameters()[0];
		try {
			ParameterTypeValidator.validateType(value, firstParameter);
			if (arguments == null || arguments.getArgs() == null || arguments.getArgs().size() == 0) {
				return (Flux<Val>) method.invoke(pip, value, ctx.getVariableCtx().getVariables());
			}
			Object[] argObjects = new Object[arguments.getArgs().size() + 2];
			int i = 0;
			argObjects[i++] = value;
			argObjects[i++] = ctx.getVariableCtx().getVariables();
			for (Expression argument : arguments.getArgs()) {
				argObjects[i++] = argument.evaluate(ctx, true, Val.undefined());
			}
			return (Flux<Val>) method.invoke(pip, argObjects);
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | IllegalParameterType e) {
			LOGGER.error(e.getMessage());
			return Flux.error(new AttributeException(e));
		}
	}

	@Override
	public final void loadPolicyInformationPoint(Object pip) throws AttributeException {
		final Class<?> clazz = pip.getClass();

		final PolicyInformationPoint pipAnnotation = clazz.getAnnotation(PolicyInformationPoint.class);

		if (pipAnnotation == null) {
			throw new AttributeException(CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION);
		}

		String pipName = pipAnnotation.name();
		if (pipName.isEmpty()) {
			pipName = clazz.getName();
		}
		attributeNamesByPipName.put(pipName, new HashSet<>());
		PolicyInformationPointDocumentation pipDocs = new PolicyInformationPointDocumentation(pipName,
				pipAnnotation.description(), pip);

		pipDocs.setName(pipAnnotation.name());
		for (Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Attribute.class)) {
				importAttribute(pip, pipName, pipDocs, method);
			}
		}
		pipDocumentations.add(pipDocs);

	}

	private void importAttribute(Object policyInformationPoint, String pipName,
			PolicyInformationPointDocumentation pipDocs, Method method) throws AttributeException {

		final Attribute attAnnotation = method.getAnnotation(Attribute.class);

		String attName = attAnnotation.name();
		if (attName.isEmpty()) {
			attName = method.getName();
		}

		int parameters = method.getParameterCount();
		if (parameters < REQUIRED_NUMBER_OF_PARAMETERS) {
			throw new AttributeException(BAD_NUMBER_OF_PARAMETERS, parameters);
		}
		final Class<?>[] parameterTypes = method.getParameterTypes();
		if (!Val.class.isAssignableFrom(parameterTypes[0])) {
			throw new AttributeException(FIRST_PARAMETER_OF_METHOD_MUST_BE_A_VALUE, parameterTypes[0].getName());
		}
		if (!Map.class.isAssignableFrom(parameterTypes[1])) {
			throw new AttributeException(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName());
		}

		final Type[] genericTypes = method.getGenericParameterTypes();

		if (!(genericTypes[1] instanceof ParameterizedType
				|| !(((ParameterizedType) genericTypes[1]).getActualTypeArguments().length == 2))) {
			throw new AttributeException(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName());
		}
		final Class<?> firstTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1]).getActualTypeArguments()[0];
		final Class<?> secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1])
				.getActualTypeArguments()[1];
		if (!String.class.isAssignableFrom(firstTypeArgument) || !JsonNode.class.isAssignableFrom(secondTypeArgument)) {
			throw new AttributeException(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName() + "<"
					+ firstTypeArgument.getName() + "," + secondTypeArgument.getName() + ">");
		}

		if (method.getParameterCount() > REQUIRED_NUMBER_OF_PARAMETERS) {
			for (int i = REQUIRED_NUMBER_OF_PARAMETERS; i < parameterTypes.length; i++) {
				if (!Flux.class.isAssignableFrom(parameterTypes[i])
						|| !(genericTypes[i] instanceof ParameterizedType)) {
					throw new AttributeException(ADDITIONAL_PARAMETER_OF_METHOD_MUST_BE_A_FLUX_OF_VALUES,
							parameterTypes[i]);
				}
				final Type fluxContentType = ((ParameterizedType) genericTypes[i]).getActualTypeArguments()[0];
				if (fluxContentType instanceof ParameterizedType
						|| !Val.class.isAssignableFrom((Class<?>) fluxContentType)) {
					throw new AttributeException(ADDITIONAL_PARAMETER_OF_METHOD_MUST_BE_A_FLUX_OF_VALUES,
							genericTypes[i]);
				}
			}
		}

		final Class<?> returnType = method.getReturnType();
		final Type genericReturnType = method.getGenericReturnType();
		if (!(genericReturnType instanceof ParameterizedType)) {
			throw new AttributeException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES, returnType.getName());
		}

		final Class<?> returnTypeArgument = (Class<?>) ((ParameterizedType) genericReturnType)
				.getActualTypeArguments()[0];
		if (!Flux.class.isAssignableFrom(returnType) || !Val.class.isAssignableFrom(returnTypeArgument)) {
			throw new AttributeException(RETURN_TYPE_MUST_BE_FLUX_OF_VALUES,
					returnType.getName() + "<" + returnTypeArgument.getName() + ">");
		}

		if (pipDocs.documentation.containsKey(attName)) {
			throw new AttributeException(ATTRIBUTE_NAME_COLLISION_PIP_CONTAINS_MULTIPLE_ATTRIBUTE_METHODS_WITH_NAME,
					attName);
		}

		pipDocs.documentation.put(attName, attAnnotation.docs());

		attributeMetadataByAttributeName.put(fullName(pipName, attName),
				new AttributeFinderMetadata(policyInformationPoint, method));

		attributeNamesByPipName.get(pipName).add(attName);
	}

	private static String fullName(String packageName, String methodName) {
		return packageName + NAME_DELIMITER + methodName;
	}

	@Override
	public Boolean provides(String attribute) {
		return attributeMetadataByAttributeName.containsKey(attribute);
	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		return Collections.unmodifiableCollection(pipDocumentations);
	}

	@Override
	public Collection<String> findersInLibrary(String pipName) {
		Collection<String> pips = attributeNamesByPipName.get(pipName);
		if (pips != null) {
			return pips;
		}
		else {
			return new HashSet<>();
		}
	}

	/**
	 * Metadata for attribute finders.
	 */
	@Data
	@AllArgsConstructor
	public static class AttributeFinderMetadata {

		@NonNull
		Object policyInformationPoint;

		@NonNull
		Method function;

	}

}
