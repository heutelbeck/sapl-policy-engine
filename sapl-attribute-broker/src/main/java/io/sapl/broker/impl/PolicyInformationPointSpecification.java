package io.sapl.broker.impl;

import static io.sapl.broker.impl.NameValidator.requireValidName;
import java.lang.annotation.Annotation;
import java.util.List;

import lombok.NonNull;

public record PolicyInformationPointSpecification(@NonNull String fullyQualifiedAttributeName,
        boolean isEnvironmentAttribute, int numberOfArguments, boolean takesVariables,
        @NonNull List<Annotation> entityValidators, @NonNull List<List<Annotation>> parameterValidators) {

    public static final int HAS_VARIABLE_NUMBER_OF_ARGUMENTS = -1;

    public PolicyInformationPointSpecification {
        requireValidName(fullyQualifiedAttributeName);
    }

    public boolean hasVariableNumberOfArguments() {
        return numberOfArguments == HAS_VARIABLE_NUMBER_OF_ARGUMENTS;
    }

    public boolean matches(PolicyInformationPointInvocation invocation) {
        // @formatter:off
        return    (invocation.fullyQualifiedAttributeName().equals(fullyQualifiedAttributeName)) 
               && (null != invocation.entity() ^ isEnvironmentAttribute)
               && (invocation.arguments().size() == numberOfArguments || hasVariableNumberOfArguments());
        // @formatter:on
    }

    /**
     * @param other another specification
     * @return true, if the presence of the two specifications leads to
     *         disambiguates in resolving PIP lookups.
     */
    public boolean collidesWith(PolicyInformationPointSpecification other) {
        if (!fullyQualifiedAttributeName.equals(other.fullyQualifiedAttributeName)
                || (isEnvironmentAttribute != other.isEnvironmentAttribute)) {
            return false;
        }
        return (hasVariableNumberOfArguments() || other.hasVariableNumberOfArguments())
                || numberOfArguments == other.numberOfArguments;
    }

}
