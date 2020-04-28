package io.sapl.vaadin;

@FunctionalInterface
public interface ValidationFinishedListener {
	void onValidationFinished(ValidationFinishedEvent event);
}
