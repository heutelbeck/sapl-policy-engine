import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';

class ShowPips extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);" theme="spacing">
 <h1>Available Policy Information Points (PIP)</h1>
 <vaadin-grid id="pipGrid"></vaadin-grid>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'show-pips';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ShowPips.is, ShowPips);
