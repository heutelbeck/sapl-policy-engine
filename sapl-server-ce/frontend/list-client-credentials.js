import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-split-layout/src/vaadin-split-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';
import '@vaadin/vaadin-checkbox/src/vaadin-checkbox.js';
import '@vaadin/vaadin-text-field/src/vaadin-password-field.js';

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
  <vaadin-vertical-layout id="editCurrentClientCredentialsLayout" style="width: 50%;">
   <vaadin-text-field label="Key" id="currentKeyTextField" style="margin: var(--lumo-space-s); width: 100%;" minlength="1" required maxlength="20" prevent-invalid-input></vaadin-text-field>
   <vaadin-vertical-layout style="width: 100%;">
    <vaadin-checkbox style="margin: var(--lumo-space-s);" value="" id="isChangingSecretCheckBox">
      Change Secret 
    </vaadin-checkbox>
    <vaadin-password-field label="Secret" placeholder="Enter secret" value="default" id="currentSecretPasswordField" style="margin: var(--lumo-space-s); width: 100%;" minlength="1" required maxlength="100" has-value></vaadin-password-field>
   </vaadin-vertical-layout>
   <vaadin-button theme="primary" style="margin: var(--lumo-space-s);" id="saveCurrentCredentialsButton">
     Save 
   </vaadin-button>
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
