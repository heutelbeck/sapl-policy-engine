import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-split-layout/src/vaadin-split-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';

class ListClientCredentials extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-s);" theme="spacing-s">
 <vaadin-button theme="primary" id="createButton">
   Create 
 </vaadin-button>
 <vaadin-split-layout style="width: 100%; height: 100%;">
  <vaadin-grid id="clientCredentialsGrid" style="width: 50%; height: 100%;"></vaadin-grid>
  <vaadin-vertical-layout id="showCurrentClientCredentialsLayout" style="width: 50%;">
   <vaadin-text-field label="Key" id="keyTextField" style="margin: var(--lumo-space-s); width: 100%;" minlength="1" required maxlength="20" prevent-invalid-input readonly invalid></vaadin-text-field>
   <vaadin-vertical-layout id="showSecretLayout">
    <vaadin-text-field label="Secret" id="secretTextField" style="width: 100%; flex-shrink: 1; margin: var(--lumo-space-s);" readonly value="*****"></vaadin-text-field>
    <div style="font-weight:bold; color: red; flex-shrink: 1; width: 100%; margin: var(--lumo-space-s);">
     The shown secret is non-recoverable. If the secret is lost, a new set of client credentials must be generated to regain access.
    </div>
   </vaadin-vertical-layout>
  </vaadin-vertical-layout>
 </vaadin-split-layout>
</vaadin-vertical-layout>
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
