package io.sapl.vaadin.annotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.sapl.vaadin.annotation.annotations.LifecycleEvent;

public class LifecycleEventTests {

	
	@Test
	void when_BeforeEnterEvent_isNotNull() {
		assertNotNull(LifecycleEvent.BEFORE_ENTER_EVENT);
	}

	@Test
	void when_BeforeLeaveEvent_isNotNull() {
		assertNotNull(LifecycleEvent.BEFORE_LEAVE_EVENT);
	}
}
