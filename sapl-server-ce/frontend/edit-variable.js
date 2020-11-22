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
import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/flow-frontend/json-editor.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';

class EditVariable extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);" theme="spacing">
 <vaadin-text-field label="Name" id="nameTextField" style="width: 300px;" minlength="1" required></vaadin-text-field>
 <json-editor style="width: 100%; height: 100%;" id="jsonEditor"></json-editor>
 <vaadin-horizontal-layout style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: flex-end;">
  <vaadin-button theme="primary" id="editButton" style="margin: var(--lumo-space-s);">
    Save 
  </vaadin-button>
  <vaadin-button theme="cancel" style="margin: var(--lumo-space-s);" id="cancelButton">
    Cancel 
  </vaadin-button>
 </vaadin-horizontal-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'edit-variable';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(EditVariable.is, EditVariable);
