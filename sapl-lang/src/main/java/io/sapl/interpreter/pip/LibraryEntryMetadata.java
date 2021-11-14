package io.sapl.interpreter.pip;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;

public interface LibraryEntryMetadata {
	static Class<?>[] VALIDATION_ANNOTATION_TYPES = { Number.class, Int.class, Long.class, Bool.class, Text.class,
			Array.class, JsonObject.class };

	String getLibraryName();

	String getFunctionName();

	String getCodeTemplate();

	String getDocumentationCodeTemplate();

	Method getFunction();

	boolean isVarArgsParameters();

	int getNumberOfParameters();

	default String fullyQualifiedName() {
		return getLibraryName() + '.' + getFunctionName();
	}

	default String getParameterName(int index) {
		return getFunction().getParameters()[index].getName();
	}

	default List<Annotation> getValidationAnnoattionsOfParameter(int index) {
		var annotations = getFunction().getParameters()[index].getAnnotations();
		var validationAnnotations = new ArrayList<Annotation>(annotations.length);
		for (var annotation : annotations)
			if (isValidationAnnotation(annotation))
				validationAnnotations.add(annotation);
		return validationAnnotations;
	}

	default boolean isValidationAnnotation(Annotation annotation) {
		for (var saplType : VALIDATION_ANNOTATION_TYPES)
			if (saplType.isAssignableFrom(annotation.getClass()))
				return true;
		return false;
	}

	default String describeParameterForDocumentation(int index) {
		return describeParameterForDocumentation(getParameterName(index), getValidationAnnoattionsOfParameter(index));
	}

	default String describeParameterForDocumentation(String name, List<Annotation> types) {
		if (types.isEmpty())
			return name;
		StringBuilder sb = new StringBuilder();
		sb.append('(');
		var numberOfTypes = types.size();
		for (var i = 0; i < numberOfTypes; i++) {
			sb.append(types.get(i).annotationType().getSimpleName());
			if (i < numberOfTypes - 1)
				sb.append('|');
		}
		sb.append(' ').append(name).append(')');
		return sb.toString();
	}

	default void appendParameterList(StringBuilder sb, int parameterOffset,
			Function<Integer, String> parameterStringBuilder) {
		if (isVarArgsParameters())
			sb.append('(').append(parameterStringBuilder.apply(parameterOffset)).append("...)");
		else if (getNumberOfParameters() > 0) {
			sb.append('(');
			for (var i = 0; i < getNumberOfParameters(); i++) {
				sb.append(parameterStringBuilder.apply(parameterOffset++));
				if (i < getNumberOfParameters() - 1)
					sb.append(", ");
			}
			sb.append(')');
		}
	}

}