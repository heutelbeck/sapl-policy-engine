package io.sapl.broker.impl.bunker;

import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

public interface EntityAttribute extends Attribute {

    Flux<Val> evaluate(Val entity, List<Val> arguments, Map<String, Val> variables);

}
