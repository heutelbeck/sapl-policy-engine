package io.sapl.broker.impl.old;

import io.sapl.api.interpreter.Val;

public interface AttributeRepository {

    void publishEnvironmentAttribute(String attributeName, Val attributeValue);

    void publishEntityAttribute(String attributeName, Val entity, Val attributeValue);

}
