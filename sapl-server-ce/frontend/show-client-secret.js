/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';

class ShowClientSecret extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);">
 <h1>Created Client</h1>
 <div style="font-weight:bold; color: red; flex-shrink: 1; width: 100%; margin: var(--lumo-space-s);" id="secretHintDiv">
  The shown secret will not be shown again and is non-recoverable. If the secret is lost, a new client must be generated to regain access.
 </div>
 <vaadin-text-field id="keyTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: stretch;" readonly label="Key"></vaadin-text-field>
 <vaadin-text-field id="secretTextField" style="align-self: stretch; margin: var(--lumo-space-s); padding: var(--lumo-space-s);" readonly label="Secret"></vaadin-text-field>
 <vaadin-horizontal-layout style="align-self: flex-end;">
  <vaadin-button id="okButton" theme="primary">
   OK
  </vaadin-button>
 </vaadin-horizontal-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'show-client-secret';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ShowClientSecret.is, ShowClientSecret);
