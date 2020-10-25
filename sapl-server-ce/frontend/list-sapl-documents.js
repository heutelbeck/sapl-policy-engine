import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-grid/src/vaadin-grid-column.js';
import '@vaadin/vaadin-grid/src/vaadin-grid-templatizer.js';
import '@polymer/iron-icon/iron-icon.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';

class ListSaplDocuments extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);" theme="spacing">
 <h1>SAPL Documents</h1>
 <vaadin-button id="createButton" theme="primary">
   Create 
 </vaadin-button>
 <vaadin-horizontal-layout style="width: 100%; height: 100%;" theme="spacing">
  <vaadin-grid id="saplDocumentGrid" style="width: 100%; height: 100%; flex: 2;"></vaadin-grid>
 </vaadin-horizontal-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'list-sapl-documents';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ListSaplDocuments.is, ListSaplDocuments);
