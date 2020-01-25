/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
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
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.interpreter.validation.IllegalParameterType;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * This Class holds the different attribute finders and PIPs as a context during evaluation.
 */
@NoArgsConstructor
public class AnnotationAttributeContext implements AttributeContext {

	private static final int REQUIRED_NUMBER_OF_PARAMETERS = 2;

	private static final String NAME_DELIMITER = ".";

	private static final String CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION = "Provided class has no @PolicyInformationPoint annotation.";

	private static final String UNKNOWN_ATTRIBUTE = "Unknown attribute %s";

	private static final String BAD_NUMBER_OF_PARAMETERS = "Bad number of parameters for attribute finder. Attribute finders are supposed to have one JsonNode and one Map<String, JsonNode> as parameters. The method had %d parameters";

	private static final String FIRST_PARAMETER_OF_METHOD_MUST_BE_A_JSON_NODE = "First parameter of method must be a JsonNode. Was: %s";

	private static final String SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP = "Second parameter of method must be a Map<String, JsonNode>. Was: %s";

	private static final String RETURN_TYPE_MUST_BE_FLUX_OF_JSON_NODE = "The return type of an attribute finder must be Flux<JsonNode>. Was: %s";

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
	public Flux<JsonNode> evaluate(String attribute, JsonNode value, Map<String, JsonNode> variables) {
		final AttributeFinderMetadata metadata = attributeMetadataByAttributeName.get(attribute);
		if (metadata == null) {
			return Flux.error(new AttributeException(String.format(UNKNOWN_ATTRIBUTE, attribute)));
		}

		final Object pip = metadata.getPolicyInformationPoint();
		final Method method = metadata.getFunction();
		final Parameter firstParameter = method.getParameters()[0];
		try {
			ParameterTypeValidator.validateType(value, firstParameter);
			return (Flux<JsonNode>) method.invoke(pip, value, variables);
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | IllegalParameterType e) {
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
		if (parameters != REQUIRED_NUMBER_OF_PARAMETERS) {
			throw new AttributeException(String.format(BAD_NUMBER_OF_PARAMETERS, parameters));
		}
		final Class<?>[] parameterTypes = method.getParameterTypes();
		if (!JsonNode.class.isAssignableFrom(parameterTypes[0])) {
			throw new AttributeException(
					String.format(FIRST_PARAMETER_OF_METHOD_MUST_BE_A_JSON_NODE, parameterTypes[0].getName()));
		}
		if (!Map.class.isAssignableFrom(parameterTypes[1])) {
			throw new AttributeException(
					String.format(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName()));
		}

		final Type[] genericTypes = method.getGenericParameterTypes();
		if (genericTypes[1] instanceof ParameterizedType
				&& ((ParameterizedType) genericTypes[1]).getActualTypeArguments().length == 2) {
			final Class<?> firstTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1])
					.getActualTypeArguments()[0];
			final Class<?> secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1])
					.getActualTypeArguments()[1];
			if (!String.class.isAssignableFrom(firstTypeArgument)
					|| !JsonNode.class.isAssignableFrom(secondTypeArgument)) {
				throw new AttributeException(
						String.format(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName() + "<"
								+ firstTypeArgument.getName() + "," + secondTypeArgument.getName() + ">"));
			}
		}
		else {
			throw new AttributeException(
					String.format(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName()));
		}

		final Class<?> returnType = method.getReturnType();
		final Type genericReturnType = method.getGenericReturnType();
		if (genericReturnType instanceof ParameterizedType) {
			final Class<?> returnTypeArgument = (Class<?>) ((ParameterizedType) genericReturnType)
					.getActualTypeArguments()[0];
			if (!Flux.class.isAssignableFrom(returnType) || !JsonNode.class.isAssignableFrom(returnTypeArgument)) {
				throw new AttributeException(String.format(RETURN_TYPE_MUST_BE_FLUX_OF_JSON_NODE,
						returnType.getName() + "<" + returnTypeArgument.getName() + ">"));
			}
		}
		else {
			throw new AttributeException(String.format(RETURN_TYPE_MUST_BE_FLUX_OF_JSON_NODE, returnType.getName()));
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
