package io.sapl.broker.impl.old;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

public interface AttributeBroker {

    Flux<Val> evaluateEntityAttribute(String attributeName, Val entity, List<Val> arguments, boolean fresh,
            Duration initialTimeOut, Map<String, Val> variables);

    Flux<Val> evaluateEntityAttribute(String attributeName, Val entity, List<Val> arguments, boolean fresh,
            Map<String, Val> variables);

    Flux<Val> evaluateEnvironmentAttribute(String attributeName, List<Val> arguments, boolean fresh,
            Duration initialTimeOut, Map<String, Val> variables);

    Flux<Val> evaluateEnvironmentAttribute(String attributeName, List<Val> arguments, boolean fresh,
            Map<String, Val> variables);

}
