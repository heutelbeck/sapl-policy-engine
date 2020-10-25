import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';

class ShowFunctionLibraries extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);" theme="spacing">
 <h1>Function Libraries</h1>
 <vaadin-grid id="libraryGrid"></vaadin-grid>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'show-function-libraries';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ShowFunctionLibraries.is, ShowFunctionLibraries);
