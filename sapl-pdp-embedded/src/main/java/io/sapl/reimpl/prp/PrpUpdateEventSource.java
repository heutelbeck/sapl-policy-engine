package io.sapl.reimpl.prp;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public interface PrpUpdateEventSource extends Disposable {
	Flux<PrpUpdateEvent> getUpdates();
}
