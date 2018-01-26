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

@NoArgsConstructor
public class AnnotationAttributeContext implements AttributeContext {

	private static final int TWO_PARAMETERS = 2;
	private static final String DOT = ".";
	private static final String VALUE_TYPE_OF_THE_MAP_MUST_BE_JSON_NODE = "Value type of the map must be JsonNode. But the key type Was: %s";
	private static final String KEY_TYPE_OF_THE_MAP_MUST_BE_STRING = "Key type of the map must be String. But the key type Was: %s";
	private static final String SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP = "Second parameter of method must be a Map<String, JsonNode>. Was: %s";
	private static final String FIRST_PARAMETER_OF_METHOD_MUST_BE_A_JSON_NODE = "First parameter of method must be a JsonNode. Was: %s";
	private static final String BAD_NUMBER_OF_PARAMETERS = "Bad number of parameters for attribute finder. Atrribute finders are supposed to have one JsonNode and one Map<String, JsonNode> as parameters. The method had %d parameters";
	private static final String UNKNOWN_ATTRIBUTE = "Unknown attribute %s";
	private static final String CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION = "Provided class has no @FunctionLibrary annotation.";

	private Collection<PolicyInformationPointDocumentation> documentation = new LinkedList<>();
	private Map<String, AttributeFinderMetadata> attributes = new HashMap<>();
	private Map<String, Collection<String>> libraries = new HashMap<>();

	public AnnotationAttributeContext(Object... libraries) throws AttributeException {
		for (Object library : libraries) {
			loadPolicyInformationPoint(library);
		}
	}

	@Override
	public JsonNode evaluate(String attribute, JsonNode value, Map<String, JsonNode> variables)
			throws AttributeException {
		AttributeFinderMetadata att = attributes.get(attribute);
		if (att == null) {
			throw new AttributeException(String.format(UNKNOWN_ATTRIBUTE, attribute));
		}
		Parameter[] attParams = att.getFunction().getParameters();
		try {
			ParameterTypeValidator.validateType(value, attParams[0]);
			return (JsonNode) att.getFunction().invoke(att.getPolicyInformationPoint(), value, variables);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| IllegalParameterType e) {
			throw new AttributeException(e);
		}
	}

	@Override
	public final void loadPolicyInformationPoint(Object policyInformationPoint) throws AttributeException {
		Class<?> clazz = policyInformationPoint.getClass();

		PolicyInformationPoint pipAnnotation = clazz.getAnnotation(PolicyInformationPoint.class);

		if (pipAnnotation == null) {
			throw new AttributeException(CLASS_HAS_NO_POLICY_INFORMATION_POINT_ANNOTATION);
		}

		String pipName = pipAnnotation.name();
		if (pipName.isEmpty()) {
			pipName = clazz.getName();
		}
		libraries.put(pipName, new HashSet<>());
		PolicyInformationPointDocumentation pipDocs = new PolicyInformationPointDocumentation(pipName,
				pipAnnotation.description(), policyInformationPoint);

		pipDocs.setName(pipAnnotation.name());
		for (Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Attribute.class)) {
				importAttribute(policyInformationPoint, pipName, pipDocs, method);
			}
		}
		documentation.add(pipDocs);

	}

	private void importAttribute(Object policyInformationPoint, String pipName,
			PolicyInformationPointDocumentation pipDocs, Method method) throws AttributeException {
		Attribute attAnnotation = method.getAnnotation(Attribute.class);
		String attName = attAnnotation.name();
		if (attName.isEmpty()) {
			attName = method.getName();
		}

		int parameters = method.getParameterCount();
		if (parameters != TWO_PARAMETERS) {
			throw new AttributeException(String.format(BAD_NUMBER_OF_PARAMETERS, parameters));
		}
		Class<?>[] parameterTypes = method.getParameterTypes();
		if (!JsonNode.class.isAssignableFrom(parameterTypes[0])) {
			throw new AttributeException(
					String.format(FIRST_PARAMETER_OF_METHOD_MUST_BE_A_JSON_NODE, parameterTypes[0].getName()));
		}
		if (!Map.class.isAssignableFrom(parameterTypes[1])) {
			throw new AttributeException(
					String.format(SECOND_PARAMETER_OF_METHOD_MUST_BE_A_MAP, parameterTypes[1].getName()));
		}

		Type[] genericTypes = method.getGenericParameterTypes();

		Class<?> fistTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1]).getActualTypeArguments()[0];
		if (!String.class.isAssignableFrom(fistTypeArgument)) {
			throw new AttributeException(String.format(KEY_TYPE_OF_THE_MAP_MUST_BE_STRING, fistTypeArgument.getName()));
		}
		Class<?> secondTypeArgument = (Class<?>) ((ParameterizedType) genericTypes[1]).getActualTypeArguments()[1];
		if (!JsonNode.class.isAssignableFrom(secondTypeArgument)) {
			throw new AttributeException(
					String.format(VALUE_TYPE_OF_THE_MAP_MUST_BE_JSON_NODE, secondTypeArgument.getName()));
		}

		pipDocs.documentation.put(attName, attAnnotation.docs());

		AttributeFinderMetadata attMeta = new AttributeFinderMetadata(policyInformationPoint, method);
		attributes.put(fullName(pipName, attName), attMeta);

		libraries.get(pipName).add(attName);
	}

	@Override
	public Boolean provides(String attribute) {
		return attributes.containsKey(attribute);
	}

	private static String fullName(String packageName, String methodName) {
		return packageName + DOT + methodName;
	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		return Collections.unmodifiableCollection(documentation);
	}

	@Override
	public Collection<String> findersInLibrary(String libraryName) {
		if (libraries.containsKey(libraryName)) {
			return libraries.get(libraryName);
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
	}

}
