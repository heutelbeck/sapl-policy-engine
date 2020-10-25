import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-area.js';

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
 <vaadin-text-area label="Name" id="nameTextArea" minlength="1"></vaadin-text-area>
 <vaadin-text-area label="JSON Value" id="jsonValueTextArea" style="align-self: stretch;" minlength="1"></vaadin-text-area>
 <vaadin-horizontal-layout style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: flex-end;">
  <vaadin-button theme="primary" id="createButton" style="margin: var(--lumo-space-s);">
    Create 
  </vaadin-button>
  <vaadin-button theme="cancel" style="margin: var(--lumo-space-s);" id="cancelButton">
    Go To Variables List 
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
