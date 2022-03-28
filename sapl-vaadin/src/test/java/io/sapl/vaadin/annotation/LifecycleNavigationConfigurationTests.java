package io.sapl.vaadin.annotation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.UIInitEvent;
import com.vaadin.flow.server.UIInitListener;
import com.vaadin.flow.server.VaadinService;

public class LifecycleNavigationConfigurationTests {

	LifecycleNavigationConfiguration sut;
	
	@BeforeEach
	void setup() {
		var vaadinNavigationPepServiceMock = mock(VaadinNavigationPepService.class);
		sut = new LifecycleNavigationConfiguration(vaadinNavigationPepServiceMock);
	}
	
	@Test
	void when_ServiceInitIsCalled_Then_AddBeforeEnterAndBeforeLeaveListenerIsCalled() {
		//GIVEN
		var serviceInitEventMock = mock(ServiceInitEvent.class);
		var vaadinServiceMock = mock(VaadinService.class);
		var eventMock = mock(UIInitEvent.class);
		var uiMock = mock(UI.class);
		when(eventMock.getUI()).thenReturn(uiMock);
		doAnswer(invocation -> {
			invocation.getArgument(0, UIInitListener.class).uiInit(eventMock);
			return null;
		}).when(vaadinServiceMock).addUIInitListener(any());
		when(serviceInitEventMock.getSource()).thenReturn(vaadinServiceMock);
		
		//WHEN
		sut.serviceInit(serviceInitEventMock);
		
		//THEN
		verify(uiMock).addBeforeEnterListener(any());
		verify(uiMock).addBeforeLeaveListener(any());
	}
}
