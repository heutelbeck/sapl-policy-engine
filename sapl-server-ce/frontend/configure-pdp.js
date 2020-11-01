import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-combo-box/src/vaadin-combo-box.js';

class ConfigurePdp extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="margin: var(--lumo-space-s);">
 <h1>PDP Configuration</h1>
 <h2>Combining Algorithm</h2>
 <div>
  The combining algorithm describes how to come to the final decision while evaluating policies 
  <a href="https://sapl.io/sapl-reference.html#combining-algorithm-2">(↗documentation)</a>.
 </div>
 <vaadin-horizontal-layout>
  <label style="align-self: center; margin: var(--lumo-space-s);">Global selection:</label>
  <vaadin-combo-box id="comboBoxCombAlgo" style="align-self: flex-start; width: 250px; margin: var(--lumo-space-s);"></vaadin-combo-box>
 </vaadin-horizontal-layout>
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
