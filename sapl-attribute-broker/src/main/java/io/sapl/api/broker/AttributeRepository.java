package io.sapl.api.broker;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import lombok.NonNull;

public interface AttributeRepository {

    void publishAttribute(@NonNull JsonNode entity, @NonNull String attributeName, @NonNull Val attributeValue);

    void publishEnvironmentAttribute(@NonNull JsonNode entity, @NonNull String attributeName,
            @NonNull Val attributeValue);

    void removeAttribute(@NonNull JsonNode entity, @NonNull String attributeName);

    void removeEnvironmentAttribute(@NonNull String attributeName);

}
