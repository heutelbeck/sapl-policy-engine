package io.sapl.reimpl.prp;

import reactor.core.publisher.Flux;

public interface PrpUpdateEventSource {
	Flux<PrpUpdateEvent> getUpdates();
}
