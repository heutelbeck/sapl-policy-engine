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
package io.sapl.server.ce.views.utils.confirm;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.templatemodel.TemplateModel;

import lombok.NonNull;
import lombok.Setter;

/**
 * A Designer generated component for the custom-confirm-dialog-content
 * template.
 *
 * Designer will add and remove fields with @Id mappings but does not overwrite
 * or otherwise change this file.
 */
@Tag("custom-confirm-dialog-content")
@JsModule("./custom-confirm-dialog-content.js")
public class CustomConfirmDialogContent
		extends PolymerTemplate<CustomConfirmDialogContent.CustomConfirmDialogContentModel> {
	@Id(value = "messageDiv")
	private Div messageDiv;

	@Id(value = "confirmButton")
	private Button confirmButton;

	@Id(value = "cancelButton")
	private Button cancelButton;

	@Setter
	private UserConfirmedListener userConfirmedListener;

	/**
	 * Creates a new CustomConfirmDialogContent.
	 * @param message a message
	 */
	public CustomConfirmDialogContent(@NonNull String message) {
		messageDiv.setText(message);

		initUi();
	}

	private void initUi() {
		confirmButton.addClickListener((ClickEvent<Button> event) -> {
			setConfirmationResult(true);
		});
		cancelButton.addClickListener((ClickEvent<Button> event) -> {
			setConfirmationResult(false);
		});
	}

	private void setConfirmationResult(boolean isConfirmed) {
		if (userConfirmedListener != null) {
			userConfirmedListener.onConfirmationSet(isConfirmed);
		}
	}

	/**
	 * This model binds properties between CustomConfirmDialogContent and
	 * custom-confirm-dialog-content
	 */
	public interface CustomConfirmDialogContentModel extends TemplateModel {
	}

	public interface UserConfirmedListener {
		void onConfirmationSet(boolean isConfirmed);
	}
}
