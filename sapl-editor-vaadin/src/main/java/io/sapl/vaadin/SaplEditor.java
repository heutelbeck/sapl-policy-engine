/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.vaadin;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.Element;
import elemental.json.JsonArray;
import elemental.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

@Tag("sapl-editor")
@JsModule("./sapl-editor.js")
@NpmPackage(value = "jquery", version = "3.4.1")
@NpmPackage(value = "codemirror", version = "5.52.2")
public class SaplEditor extends BaseEditor {

	private final List<ValidationFinishedListener> validationFinishedListeners;
	
	public SaplEditor(SaplEditorConfiguration config) {
		this.validationFinishedListeners = new ArrayList<>();
		
		Element element = getElement();
		applyBaseConfiguration(element, config);
	}
	
	@ClientCallable
	protected void onValidation(JsonArray jsonIssues) {
		int length = jsonIssues.length();
		List<Issue> issues = new ArrayList<Issue>(length);
		for (int i = 0; i < length; i++) {
			JsonObject jsonIssue = jsonIssues.getObject(i);
			Issue issue = new Issue(jsonIssue);
			issues.add(issue);
		}

		for (ValidationFinishedListener listener : validationFinishedListeners) {
			Issue[] issueArray = issues.toArray(new Issue[0]);
			listener.onValidationFinished(new ValidationFinishedEvent(issueArray));
		}
    }
	
	/**
	 * Registers a validation finished listener. The validation changed event will
	 * be raised after the document was changed and the validation took place. The
	 * event object contains a list with all validation issues of the document.
	 * 
	 * @param listener
	 */
	public void addValidationFinishedListener(ValidationFinishedListener listener) {
		this.validationFinishedListeners.add(listener);
	}

	/**
	 * Removes a registered validation finished listener.
	 * 
	 * @param listener The registered listener that should be removed.
	 */
	public void removeValidationFinishedListener(ValidationFinishedListener listener) {
		this.validationFinishedListeners.remove(listener);
	}
}