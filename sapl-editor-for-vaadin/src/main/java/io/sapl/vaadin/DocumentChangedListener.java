package io.sapl.vaadin;

@FunctionalInterface
public interface DocumentChangedListener {
	void onDocumentChanged(DocumentChangedEvent event);
}
