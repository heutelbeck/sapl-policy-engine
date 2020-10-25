import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-combo-box/src/vaadin-combo-box.js';
import '@vaadin/vaadin-combo-box/src/vaadin-combo-box-item.js';

class ConfigureCombiningAlgorithm extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-m);" theme="spacing">
 <h1>Combining Algorithm</h1>
 <vaadin-combo-box id="selectionComboBox" style="align-self: stretch;"></vaadin-combo-box>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'configure-combining-algorithm';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ConfigureCombiningAlgorithm.is, ConfigureCombiningAlgorithm);
