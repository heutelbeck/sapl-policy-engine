package io.sapl.broker.impl;

import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import lombok.NonNull;

public record PolicyInformationPointInvocation(String fullyQualifiedAttributeName, Val entity, List<Val> arguments,
        Map<String, Val> variables) {
    public PolicyInformationPointInvocation(@NonNull String fullyQualifiedAttributeName, Val entity,
            @NonNull List<Val> arguments, @NonNull Map<String, Val> variables) {
        NameValidator.assertIsValidName(fullyQualifiedAttributeName);
        this.fullyQualifiedAttributeName = fullyQualifiedAttributeName;
        this.entity                      = entity;
        this.arguments                   = arguments;
        this.variables                   = variables;
    }

}