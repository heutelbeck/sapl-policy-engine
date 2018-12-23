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
import reactor.core.scheduler.Schedulers;

@NoArgsConstructor
public class AnnotationAttributeContext implements AttributeContext {

	private static final int REQUIRED_NUMBER_OF_PARAMETERS = 2;
	private static final String NAME_DELIMITER = ".";
	private static final String CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION = "Provided class has no @PolicyInformationPoint annotation.";
	private static final String UNKNOWN_ATTRIBUTE = "Unknown attribute %s";
	private static final String BAD_NUMBER_OF_PARAMETERS = "Bad number of parameters for attribute finder. Attribute finders are supposed to have one JsonNode and one Map<String, JsonNode> as parameters. The method had %d parameters";
	private static final String FIRST_PARAMETER_OF_METHOD_MUST_BE_A_JSON_NODE = "First parameter of method must be a JsonNode. Was: %s";
	private static final String SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP = "Second parameter of method must be a Map<String, JsonNode>. Was: %s";
	private static final String KEY_TYPE_OF_THE_MAP_MUST_BE_STRING = "Key type of the map must be String. Was: %s";
	private static final String VALUE_TYPE_OF_THE_MAP_MUST_BE_JSON_NODE = "Value type of the map must be JsonNode. Was: %s";
	private static final String RETURN_TYPE_OF_NON_REACTIVE_METHOD_MUST_BE_JSON_NODE = "The return type of a non reactive attribute finder must be JsonNode. Was: %s";
	private static final String RETURN_TYPE_OF_REACTIVE_METHOD_MUST_BE_FLUX_OF_JSON_NODE = "The return type of a reactive attribute finder must be Flux<JsonNode>. Was: %s";

	private Map<String, Collection<String>> attributeNamesByPipName = new HashMap<>();
	private Map<String, AttributeFinderMetadata> attributeMetadataByAttributeName = new HashMap<>();
	private Collection<PolicyInformationPointDocumentation> pipDocumentations = new LinkedList<>();

	public AnnotationAttributeContext(Object... policyInformationPoints) throws AttributeException {
		for (Object pip : policyInformationPoints) {
			loadPolicyInformationPoint(pip);
		}
	}

	@Override
	public JsonNode evaluate(String attribute, JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		final AttributeFinderMetadata metadata = attributeMetadataByAttributeName.get(attribute);
		if (metadata == null) {
			throw new AttributeException(String.format(UNKNOWN_ATTRIBUTE, attribute));
		}

		final Object pip = metadata.getPolicyInformationPoint();
		final Method method = metadata.getFunction();
		final Parameter firstParameter = method.getParameters()[0];
		try {
			ParameterTypeValidator.validateType(value, firstParameter);
			if (metadata.isReactive()) {
				return ((Flux<JsonNode>) method.invoke(pip, value, variables)).blockFirst();
			} else {
				return (JsonNode) method.invoke(pip, value, variables);
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | IllegalParameterType e) {
			throw new AttributeException(e);
		}
	}

	@Override
	public Flux<JsonNode> reactiveEvaluate(String attribute, JsonNode value, Map<String, JsonNode> variables) {
		final AttributeFinderMetadata metadata = attributeMetadataByAttributeName.get(attribute);
		if (metadata == null) {
			return Flux.error(new AttributeException(String.format(UNKNOWN_ATTRIBUTE, attribute)));
		}

		final Object pip = metadata.getPolicyInformationPoint();
		final Method method = metadata.getFunction();
		final Parameter firstParameter = method.getParameters()[0];
		try {
			ParameterTypeValidator.validateType(value, firstParameter);
			if (metadata.isReactive()) {
                final Flux<JsonNode> resultFlux = (Flux<JsonNode>) method.invoke(pip, value, variables);
                return resultFlux.subscribeOn(Schedulers.elastic());
            } else {
				final JsonNode jsonNode = (JsonNode) method.invoke(pip, value, variables);
				return Flux.just(jsonNode);
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | IllegalParameterType e) {
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

	private void importAttribute(Object policyInformationPoint, String pipName, PolicyInformationPointDocumentation pipDocs, Method method)
			throws AttributeException {

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
			throw new AttributeException(String.format(FIRST_PARAMETER_OF_METHOD_MUST_BE_A_JSON_NODE, parameterTypes[0].getName()));
		}
		if (!Map.class.isAssignableFrom(parameterTypes[1])) {
			throw new AttributeException(String.format(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName()));
		}

		final Type[] genericTypes = method.getGenericParameterTypes();
		final Class<?> fistTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1]).getActualTypeArguments()[0];
		if (!String.class.isAssignableFrom(fistTypeArgument)) {
			throw new AttributeException(String.format(KEY_TYPE_OF_THE_MAP_MUST_BE_STRING, fistTypeArgument.getName()));
		}
		final Class<?> secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1]).getActualTypeArguments()[1];
		if (!JsonNode.class.isAssignableFrom(secondTypeArgument)) {
			throw new AttributeException(String.format(VALUE_TYPE_OF_THE_MAP_MUST_BE_JSON_NODE, secondTypeArgument.getName()));
		}

		final boolean isReactive = attAnnotation.reactive();

		final Class<?> returnType = method.getReturnType();
		if (isReactive) {
			final Type genericReturnType = method.getGenericReturnType();
			final Class<?> returnTypeArgument = (Class<?>) ((ParameterizedType) genericReturnType).getActualTypeArguments()[0];
			if (!Flux.class.isAssignableFrom(returnType) || !JsonNode.class.isAssignableFrom(returnTypeArgument)) {
				throw new AttributeException(String.format(RETURN_TYPE_OF_REACTIVE_METHOD_MUST_BE_FLUX_OF_JSON_NODE, returnType.getName() + "<" + returnTypeArgument.getName() + ">"));
			}
		} else {
			if (!JsonNode.class.isAssignableFrom(returnType)) {
				throw new AttributeException(String.format(RETURN_TYPE_OF_NON_REACTIVE_METHOD_MUST_BE_JSON_NODE, returnType.getName()));
			}
		}

		pipDocs.documentation.put(attName, attAnnotation.docs());

		attributeMetadataByAttributeName.put(fullName(pipName, attName), new AttributeFinderMetadata(policyInformationPoint, method, isReactive));

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
		if (attributeNamesByPipName.containsKey(pipName)) {
			return attributeNamesByPipName.get(pipName);
		} else {
			return new HashSet<>();
		}
	}

	@Data
	@AllArgsConstructor
	public static class AttributeFinderMetadata {

		@NonNull
		Object policyInformationPoint;

		@NonNull
		Method function;

		@NonNull
		boolean isReactive;
	}

}
