package io.sapl.broker.api;

import io.sapl.api.interpreter.Val;

public interface AttributeRepository {

    void publishEnvironmentAttribute(String attributeName, Val attributeValue);

    void publishEntityAttribute(String attributeName, Val entity, Val attributeValue);

}
