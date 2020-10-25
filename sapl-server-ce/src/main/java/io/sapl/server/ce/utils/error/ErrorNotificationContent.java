package io.sapl.server.ce.utils.error;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.templatemodel.TemplateModel;

import lombok.NonNull;

/**
 * Content of an error notification.
 */
@Tag("error-notification-content")
@JsModule("./error-notification-content.js")
public class ErrorNotificationContent extends PolymerTemplate<ErrorNotificationContent.ErrorNotificationContentModel> {
	@Id(value = "errorMessageDiv")
	private Div errorMessageDiv;

	/**
	 * Creates a new ErrorNotificationContent.
	 */
	public ErrorNotificationContent(@NonNull String errorMessage) {
		this.errorMessageDiv.setText(errorMessage);
	}

	/**
	 * This model binds properties between ErrorNotificationContent and
	 * error-notification-content
	 */
	public interface ErrorNotificationContentModel extends TemplateModel {
	}
}
