package io.sapl.spring.method.metadata;

import org.springframework.expression.Expression;

public record SaplAttribute(Class<?> annotationType, Expression subjectExpression, Expression actionExpression,
		Expression resourceExpression, Expression environmentExpression, Class<?> genericsType) {

	public static final SaplAttribute NULL_ATTRIBUTE = new SaplAttribute(null, null, null, null, null, null);

	@Override
	public String toString() {
		return "@" + (annotationType() == null ? "null" : annotationType().getSimpleName()) + "(subject="
				+ expressionStringOrNull(subjectExpression())
				+ ", action=" + expressionStringOrNull(actionExpression()) + ", resource="
				+ expressionStringOrNull(resourceExpression()) + ", environment="
				+ expressionStringOrNull(environmentExpression()) + ", genericsType=" + (genericsType() == null ? "null"
						: genericsType().getName())
				+ ")";
	}

	private String expressionStringOrNull(Expression expression) {
		if (expression == null) {
			return "null";
		}
		return '"' + expression.getExpressionString() + '"';
	}
}
