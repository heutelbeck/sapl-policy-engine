import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';

class CreateVariable extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);" theme="spacing">
 <h1 style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);">Create Variable</h1>
 <vaadin-text-field id="nameTextField" style="flex-shrink: 1; margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: stretch;" minlength="1" maxlength="50" label="Name"></vaadin-text-field>
 <vaadin-horizontal-layout style="align-self: flex-end; flex-grow: 0;" theme="spacing">
  <vaadin-button id="createButton" theme="primary">
   Create
  </vaadin-button>
  <vaadin-button id="cancelButton">
   Cancel
  </vaadin-button>
 </vaadin-horizontal-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'create-variable';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(CreateVariable.is, CreateVariable);
