package io.sapl.springdatamongoreactive.sapl.utils;

import io.sapl.springdatamongoreactive.sapl.Operator;
import reactor.util.annotation.Nullable;

public record SaplCondition(String field, Object value, Operator operator, @Nullable String conjunction) {
    public SaplCondition(String field, Object value, Operator operator, @Nullable String conjunction) {
        this.field       = field;
        this.value       = value;
        this.operator    = operator;
        this.conjunction = conjunction == null || "and".equalsIgnoreCase(conjunction) ? "And" : "Or";
    }
}
