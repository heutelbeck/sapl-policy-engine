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
