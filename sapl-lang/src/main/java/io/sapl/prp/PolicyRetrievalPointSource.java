package io.sapl.prp;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public interface PolicyRetrievalPointSource extends Disposable {

    Flux<PolicyRetrievalPoint> policyRetrievalPoint();

}
