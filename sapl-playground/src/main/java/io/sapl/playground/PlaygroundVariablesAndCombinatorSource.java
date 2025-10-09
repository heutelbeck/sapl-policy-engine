package io.sapl.playground;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Optional;

public class PlaygroundVariablesAndCombinatorSource implements VariablesAndCombinatorSource {

    private final Sinks.Many<Optional<PolicyDocumentCombiningAlgorithm>> combiningAlgorithmSink;
    private final Sinks.Many<Optional<Map<String, Val>>>                 variablesSink;

    private final Flux<Optional<PolicyDocumentCombiningAlgorithm>> combiningAlgorithmFlux;
    private final Flux<Optional<Map<String, Val>>>                 variablesFlux;

    public PlaygroundVariablesAndCombinatorSource() {
        combiningAlgorithmSink = Sinks.many().replay().latest();
        variablesSink          = Sinks.many().replay().latest();

        combiningAlgorithmFlux = combiningAlgorithmSink.asFlux();
        variablesFlux          = variablesSink.asFlux();
    }

    public void setCombiningAlgorithm(PolicyDocumentCombiningAlgorithm algorithm) {
        combiningAlgorithmSink.tryEmitNext(Optional.ofNullable(algorithm));
    }

    public void setVariables(Map<String, Val> variables) {
        variablesSink.tryEmitNext(Optional.ofNullable(variables));
    }

    @Override
    public Flux<Optional<PolicyDocumentCombiningAlgorithm>> getCombiningAlgorithm() {
        return combiningAlgorithmFlux;
    }

    @Override
    public Flux<Optional<Map<String, Val>>> getVariables() {
        return variablesFlux;
    }

    @Override
    @PreDestroy
    public void destroy() {
        combiningAlgorithmSink.tryEmitComplete();
        variablesSink.tryEmitComplete();
    }
}
