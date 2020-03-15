package io.sapl.vaadin;

public class DocumentChangedEvent {
	private String newValue;
	
	public DocumentChangedEvent(String newValue) {
		this.newValue = newValue;
	}
	
	public String getNewValue() {
		return this.newValue;
	}
}
