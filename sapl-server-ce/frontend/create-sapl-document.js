import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/flow-frontend/sapl-editor.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';

class CreateSaplDocument extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%;">
 <h1>Create SAPL Document</h1>
 <sapl-editor id="saplEditor" style="flex-grow: 1; align-self: stretch; margin: var(--lumo-space-s); padding: var(--lumo-space-s);">
   policy "set by Vaadin View after instantiation -&gt;\u2588&lt;-" permit 
 </sapl-editor>
 <vaadin-horizontal-layout style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: flex-end;">
  <vaadin-button theme="primary" id="createButton" style="margin: var(--lumo-space-s);">
    Create 
  </vaadin-button>
  <vaadin-button theme="cancel" style="margin: var(--lumo-space-s);" id="cancelButton">
   Go To Document List
  </vaadin-button>
 </vaadin-horizontal-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'create-sapl-document';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(CreateSaplDocument.is, CreateSaplDocument);
