import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-combo-box/src/vaadin-combo-box.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';

class ConfigurePdp extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-s);" theme="spacing-s">
 <h1>Combining Algorithm</h1>
 <div style="margin: var(--lumo-space-s);">
   The combining algorithm describes how to come to the final decision while evaluating policies 
  <a href="https://sapl.io/sapl-reference.html#combining-algorithm-2">(↗documentation)</a>. 
 </div>
 <vaadin-horizontal-layout style="margin: var(--lumo-space-s);">
  <label style="align-self: center;">Global selection:</label>
  <vaadin-combo-box id="comboBoxCombAlgo" style="align-self: flex-start; width: 250px; margin: var(--lumo-space-s);"></vaadin-combo-box>
 </vaadin-horizontal-layout>
 <h1>Variables</h1>
 <vaadin-button theme="primary" id="createVariableButton" style="margin: var(--lumo-space-s);">
   Create 
 </vaadin-button>
 <vaadin-grid id="variablesGrid" style="margin: var(--lumo-space-s);"></vaadin-grid>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'configure-pdp';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ConfigurePdp.is, ConfigurePdp);
