import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-split-layout/src/vaadin-split-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';

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
                <vaadin-grid id="clientCredentialsGrid" style="width: 100%; height: 100%;"></vaadin-grid>
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
