package io.sapl.playground;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalPointSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@RequiredArgsConstructor
public class PlaygroundPolicyRetrievalPointSource implements PolicyRetrievalPointSource {

    private final SAPLInterpreter parser;

    private final Sinks.Many<PolicyRetrievalPoint> prpSink = Sinks.many().replay().latest();
    private final Flux<PolicyRetrievalPoint> prpFlux = prpSink.asFlux();

    public void updatePrp(List<String> documents) {
        val prp = new PlaygroundPolicyRetrievalPoint(documents, parser);
        prpSink.tryEmitNext(prp);
    }

    @Override
    public Flux<PolicyRetrievalPoint> policyRetrievalPoint() {
        return prpFlux;
    }

    @Override
    @PreDestroy
    public void dispose() {
        prpSink.tryEmitComplete();
    }
}