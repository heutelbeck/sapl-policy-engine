package io.sapl.vaadin.annotation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "module.enabled", havingValue = "true", matchIfMissing = true)
public class LifecycleNavigationConfiguration implements VaadinServiceInitListener {

	private static final long serialVersionUID = 1119604590954774449L;
	private final VaadinNavigationPepService vaadinNavigationPepService;

	@Override
	public void serviceInit(ServiceInitEvent event) {

		event.getSource().addUIInitListener(uiEvent -> {
			final UI ui = uiEvent.getUI();
			ui.addBeforeEnterListener(vaadinNavigationPepService::beforeEnter);
			ui.addBeforeLeaveListener(vaadinNavigationPepService::beforeLeave);
		});
	}

}