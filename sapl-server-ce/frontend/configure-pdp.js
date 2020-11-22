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
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-combo-box/src/vaadin-combo-box.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';

class ConfigurePdp extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-s);" theme="spacing-s">
 <h1>Combining Algorithm</h1>
 <div style="margin: var(--lumo-space-s);">
   The combining algorithm describes how to come to the final decision while evaluating policies 
  <a href="https://sapl.io/sapl-reference.html#combining-algorithm-2">(↗documentation)</a>. 
 </div>
 <vaadin-horizontal-layout style="margin: var(--lumo-space-s);">
  <label style="align-self: center;">Global selection:</label>
  <vaadin-combo-box id="comboBoxCombAlgo" style="align-self: flex-start; width: 250px; margin: var(--lumo-space-s);"></vaadin-combo-box>
 </vaadin-horizontal-layout>
 <h1>Variables</h1>
 <vaadin-button theme="primary" id="createVariableButton" style="margin: var(--lumo-space-s);">
   Create 
 </vaadin-button>
 <vaadin-grid id="variablesGrid" style="margin: var(--lumo-space-s);"></vaadin-grid>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'configure-pdp';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ConfigurePdp.is, ConfigurePdp);
