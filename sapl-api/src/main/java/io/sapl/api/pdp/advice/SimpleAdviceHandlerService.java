package io.sapl.api.pdp.advice;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class SimpleAdviceHandlerService implements AdviceHandlerService {

	private final List<AdviceHandler> handlers = new LinkedList<>();

	@Override
	public void register(AdviceHandler adviceHandler) {
		handlers.add(adviceHandler);
	}

	@Override
	public void unregister(AdviceHandler adviceHandler) {
		handlers.remove(adviceHandler);
	}

	@Override
	public List<AdviceHandler> registeredHandlers() {
		return Collections.unmodifiableList(handlers);
	}

	@Override
	public void unregisterAll() {
		handlers.clear();
	}

}
