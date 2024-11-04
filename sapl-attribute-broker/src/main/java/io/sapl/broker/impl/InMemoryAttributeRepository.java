package io.sapl.broker.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.broker.AttributeRepository;
import io.sapl.api.broker.AttributeStreamBroker;
import io.sapl.api.interpreter.Val;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InMemoryAttributeRepository implements AttributeRepository {

    private final AttributeStreamBroker broker;

    private record AttributeKey(JsonNode entity, String attributeName) {}

    private final Map<AttributeKey, Val> repository = new ConcurrentHashMap<>();;

    @Override
    public void publishAttribute(@NonNull JsonNode entity, @NonNull String attributeName, @NonNull Val attributeValue) {
        repository.put(new AttributeKey(entity, attributeName), attributeValue);
    }

    @Override
    public void publishEnvironmentAttribute(@NonNull JsonNode entity, @NonNull String attributeName,
            @NonNull Val attributeValue) {
        repository.put(new AttributeKey(null, attributeName), attributeValue);
    }

    @Override
    public void removeAttribute(@NonNull JsonNode entity, @NonNull String attributeName) {
        repository.remove(new AttributeKey(entity, attributeName));
    }

    @Override
    public void removeEnvironmentAttribute(@NonNull String attributeName) {
        repository.remove(new AttributeKey(null, attributeName));
    }

}
