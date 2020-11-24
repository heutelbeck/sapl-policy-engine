/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.server.ce.views.utils.error;

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
		errorMessageDiv.setText(errorMessage);
	}

	/**
	 * This model binds properties between ErrorNotificationContent and
	 * error-notification-content
	 */
	public interface ErrorNotificationContentModel extends TemplateModel {
	}
}
