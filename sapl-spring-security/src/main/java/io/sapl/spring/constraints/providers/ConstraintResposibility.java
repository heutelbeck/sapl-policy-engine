package io.sapl.spring.constraints.providers;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ConstraintResposibility {
    private static final String TYPE = "type";

    public static boolean isResponsible(JsonNode constraint, String requiredType) {
        if (constraint == null || !constraint.isObject())
            return false;

        var type = constraint.get(TYPE);

        if (Objects.isNull(type) || !type.isTextual())
            return false;

        return Objects.equals(type.asText(), requiredType);
    }
}
