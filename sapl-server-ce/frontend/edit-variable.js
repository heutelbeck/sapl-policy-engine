import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-area.js';

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
 <vaadin-text-area label="Name" id="nameTextArea" minlength="1" has-value style="width: 300px;"></vaadin-text-area>
 <vaadin-text-area label="JSON Value" id="jsonValueTextArea" style="align-self: stretch; flex-grow: 1; width: 100%; height: 100%; flex-shrink: 1;" minlength="1"></vaadin-text-area>
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
