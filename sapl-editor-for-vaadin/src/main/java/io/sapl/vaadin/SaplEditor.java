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

import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.DirectProcessor;

@Tag("sapl-editor")
@JavaScript("jquery/dist/jquery.min.js")
@JavaScript("./sapl-editor.js")
@NpmPackage(value = "jquery", version = "3.4.1")
@NpmPackage(value = "codemirror", version = "5.51.0")
public class SaplEditor extends Component {
	
	private DirectProcessor<String> documentChangedProcessor;
	private ConnectableFlux<String> documentChangedFlux;
	
	public SaplEditor(SaplEditorConfiguration config) {
		getElement().setProperty("hasLineNumbers", config.HasLineNumbers);
		getElement().setProperty("autoCloseBrackets", config.AutoCloseBrackets);
		getElement().setProperty("matchBrackets", config.MatchBrackets);
		getElement().setProperty("textUpdateDelay", config.TextUpdateDelay);
		
		this.documentChangedProcessor = DirectProcessor.create();
		this.documentChangedFlux = documentChangedProcessor.publish();
		this.documentChangedFlux.connect();
	}
	
	@ClientCallable
	public void onDocumentChanged(String newValue) {
		this.documentChangedProcessor.onNext(newValue);
	}
	
	public void setValue(String value) {
		getElement().setProperty("document", value);
	}
	
	public ConnectableFlux<String> getDocumentChangedFlux() {
		return this.documentChangedFlux;
	}

}