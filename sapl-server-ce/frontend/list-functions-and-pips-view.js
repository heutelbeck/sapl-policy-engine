import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-split-layout/src/vaadin-split-layout.js';
import '@vaadin/vaadin-grid/src/vaadin-grid.js';

class ListFunctionsAndPipsView extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%; padding: var(--lumo-space-s);" theme="spacing-s">
 <vaadin-vertical-layout style="height: 50%; width: 100%; flex-grow: 0;" theme="spacing-xs">
  <h1>Functions Libraries</h1>
  <vaadin-split-layout style="width: 100%; flex-grow: 0; height: 100%;">
   <vaadin-grid id="functionLibsGrid" style="width: 30%; height: 100%;"></vaadin-grid>
   <vaadin-vertical-layout id="showCurrentFunctionLibLayout" style="width: 70%;">
    <div id="descriptionOfCurrentFunctionLibDiv" style="width: 100%;">
      Description 
    </div>
    <vaadin-grid id="functionsOfCurrentFunctionLibGrid" style="height: 100%;"></vaadin-grid>
   </vaadin-vertical-layout>
  </vaadin-split-layout>
 </vaadin-vertical-layout>
 <vaadin-vertical-layout style="height: 50%; width: 100%;" theme="spacing-xs">
  <h1>Policy Information Points</h1>
  <vaadin-split-layout style="width: 100%; height: 100%;">
   <vaadin-grid id="pipsGrid" style="width: 30%; height: 100%;"></vaadin-grid>
   <vaadin-vertical-layout id="showCurrentPipLayout" style="width: 70%;">
    <div id="descriptionOfCurrentPipDiv" style="width: 100%;">
      Description 
    </div>
    <vaadin-grid id="functionsOfCurrentPipGrid" style="height: 100%;"></vaadin-grid>
   </vaadin-vertical-layout>
  </vaadin-split-layout>
 </vaadin-vertical-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'list-functions-and-pips-view';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ListFunctionsAndPipsView.is, ListFunctionsAndPipsView);
