package io.sapl.broker.impl;

import java.lang.annotation.Annotation;
import java.util.List;

import lombok.NonNull;

public record PolicyInformationPointSpecification(String fullyQualifiedAttributeName,
        boolean isEnvironmentAttribute, int numberOfArguments, boolean takesVariables,
        List<Annotation> entityValidators, List<List<Annotation>> parameterValidators) {

    public static final int HAS_VARIABLE_NUMBER_OF_ARGUMENTS = -1;

    public PolicyInformationPointSpecification(@NonNull String fullyQualifiedAttributeName,
            boolean isEnvironmentAttribute, int numberOfArguments, boolean takesVariables,
            @NonNull List<Annotation> entityValidators, @NonNull List<List<Annotation>> parameterValidators) {
        NameValidator.assertIsValidName(fullyQualifiedAttributeName);
        this.fullyQualifiedAttributeName = fullyQualifiedAttributeName;
        this.isEnvironmentAttribute      = isEnvironmentAttribute;
        this.numberOfArguments           = numberOfArguments;
        this.takesVariables              = takesVariables;
        this.entityValidators            = entityValidators;
        this.parameterValidators         = parameterValidators;
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
}