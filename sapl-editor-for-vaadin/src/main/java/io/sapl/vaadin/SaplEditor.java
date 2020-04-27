/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.Element;

import elemental.json.JsonArray;
import elemental.json.JsonObject;

@Tag("sapl-editor")
@JavaScript("jquery/dist/jquery.min.js")
@JavaScript("./sapl-editor.js")
@NpmPackage(value = "jquery", version = "3.4.1")
@NpmPackage(value = "codemirror", version = "5.51.0")
public class SaplEditor extends Component {
	
	private List<DocumentChangedListener> documentChangedListeners;
	private List<ValidationFinishedListener> validationFinishedListeners;
	
	public SaplEditor(SaplEditorConfiguration config) {
		Element element = getElement();
		element.setProperty("hasLineNumbers", config.isHasLineNumbers());
		element.setProperty("autoCloseBrackets", config.isAutoCloseBrackets());
		element.setProperty("matchBrackets", config.isMatchBrackets());
		element.setProperty("textUpdateDelay", config.getTextUpdateDelay());
		
		this.documentChangedListeners = new ArrayList<>();
		this.validationFinishedListeners = new ArrayList<>();
	}
	
	@ClientCallable
	public void onDocumentChanged(String newValue) {
		for (DocumentChangedListener listener : documentChangedListeners) {
			listener.onDocumentChanged(new DocumentChangedEvent(newValue));
		}
	}
	
	@ClientCallable
	public void onValidation(JsonArray jsonIssues) {	
		List<Issue> issues = new ArrayList<Issue>();
		Integer length = jsonIssues.length();
		for (int i = 0; i < length; i++) {
			JsonObject jsonIssue = jsonIssues.getObject(i);
			Issue issue = JsonToIssueConverter.Convert(jsonIssue);
			issues.add(issue);
		}
		
		for (ValidationFinishedListener listener : validationFinishedListeners) {
			Issue[] issueArray = issues.toArray(new Issue[0]);
			listener.onValidationFinished(new ValidationFinishedEvent(issueArray));
		}
	}
	
	public void setValue(String value) {
		Element element = getElement();
		element.setProperty("document", value);
	}
	
	public void addListener(DocumentChangedListener listener) {
		this.documentChangedListeners.add(listener);
	}
	
	public void addListener(ValidationFinishedListener listener) {
		this.validationFinishedListeners.add(listener);
	}
	
	public void validateDocument() {
		Element element = getElement();
		element.callJsFunction("validateDocument", element);
	}
	
	@ClientCallable
	public void onFirstUpdated() {
		Element element = getElement();
		element.callJsFunction("onFirstUpdated", element);
	}
}