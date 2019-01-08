package io.sapl.pep.pdp.obligation;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import io.sapl.api.pdp.obligation.ObligationHandler;
import io.sapl.api.pdp.obligation.ObligationHandlerService;

public class SimpleObligationHandlerService implements ObligationHandlerService {

	private final List<ObligationHandler> handlers = new LinkedList<>();

	@Override
	public void register(ObligationHandler obligationHandler) {
		handlers.add(obligationHandler);
	}

	@Override
	public void unregister(ObligationHandler obligationHandler) {
		handlers.remove(obligationHandler);
	}

	@Override
	public List<ObligationHandler> registeredHandlers() {
		return Collections.unmodifiableList(handlers);
	}

	@Override
	public void unregisterAll() {
		handlers.clear();
	}

}
