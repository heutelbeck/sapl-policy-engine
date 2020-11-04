import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-split-layout/src/vaadin-split-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';

class ListClientCredentials extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-button theme="primary">
 Add
</vaadin-button>
<vaadin-split-layout style="width: 100%; height: 100%;">
 <vaadin-grid id="clientCredentialsGrid" style="width: 100%;"></vaadin-grid>
 <vaadin-vertical-layout>
  <vaadin-text-field label="Key" id="currentKeyTextField"></vaadin-text-field>
  <vaadin-text-field label="Secret" placeholder="Placeholder" id="currentSecretTextField">
    currentKeyTextField
  </vaadin-text-field>
  <vaadin-button theme="primary">
   Save
  </vaadin-button>
 </vaadin-vertical-layout>
</vaadin-split-layout>
`;
    }

    static get is() {
        return 'list-client-credentials';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ListClientCredentials.is, ListClientCredentials);
