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
import { PolymerElement } from '@polymer/polymer/polymer-element.js';
import { html } from '@polymer/polymer/lib/utils/html-tag.js';
import '@vaadin/vaadin-split-layout/src/vaadin-split-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/flow-frontend/sapl-editor.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';

class PublishedPoliciesView extends PolymerElement {
    static get template() {
        return html`
<style include="shared-styles">
        :host {
          display: block;
        }
      </style>
<vaadin-split-layout style="width: 100%; height: 100%;">
 <vaadin-grid id="grid" style="width: 30%;"></vaadin-grid>
 <vaadin-vertical-layout theme="spacing" id="layoutForSelectedPublishedDocument" style="width: 70%; height: 100%;">
  <vaadin-horizontal-layout theme="spacing" style="width: 100%;">
   <vaadin-text-field label="Policy Identifier" placeholder="Placeholder" id="policyIdTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); flex-grow: 1; align-self: stretch; flex-shrink: 0;" readonly></vaadin-text-field>
   <vaadin-text-field label="Published Version" placeholder="Placeholder" id="publishedVersionTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); flex-grow: 1; align-self: stretch; flex-shrink: 0;" readonly></vaadin-text-field>
  </vaadin-horizontal-layout>
  <vaadin-button id="openEditPageForPolicyButton" style="margin: var(--lumo-space-s);" theme="primary">
   Manage Policy
  </vaadin-button>
  <sapl-editor id="saplEditor" style="width: 100%;"></sapl-editor>
 </vaadin-vertical-layout>
</vaadin-split-layout>
`;
    }

    static get is() {
        return 'published-policies-view';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(PublishedPoliciesView.is, PublishedPoliciesView);
