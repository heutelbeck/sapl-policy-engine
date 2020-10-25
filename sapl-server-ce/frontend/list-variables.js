import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';

class ListVariables extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);" theme="spacing">
 <h1>Variables</h1>
 <vaadin-button id="createButton" theme="primary">
   Create 
 </vaadin-button>
 <vaadin-horizontal-layout style="width: 100%; height: 100%;">
  <vaadin-grid id="variablesGrid" style="width: 100%; height: 100%; flex: 2;"></vaadin-grid>
 </vaadin-horizontal-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'list-variables';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ListVariables.is, ListVariables);
