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
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@polymer/iron-icon/iron-icon.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-area.js';

class ErrorNotificationContent extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-horizontal-layout style="width: 100%; height: 100%;">
 <iron-icon style="align-self: center; margin: var(--lumo-space-s); padding: var(--lumo-space-s); color: red; flex-shrink: 0; height: 50px;
  width: 50px;" icon="vaadin:close-circle"></iron-icon>
 <div id="errorMessageDiv" style="padding: var(--lumo-space-s); margin: var(--lumo-space-s); align-self: center; flex-grow: 1; color: rgb(255, 100, 100)">
  default error message
 </div>
</vaadin-horizontal-layout>
`;
    }

    static get is() {
        return 'error-notification-content';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ErrorNotificationContent.is, ErrorNotificationContent);
