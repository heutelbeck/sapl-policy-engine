export class SAPLEditor extends HTMLElement {
	
	constructor() {
		super();
		this.shadow = this.attachShadow({mode: 'open'});
		this.shadow.innerHTML = SAPLEditor.template();
	}	
	
	connectedCallback() {
		var _this = this;
		require(["codemirror/addon/edit/matchbrackets",
				 "codemirror/addon/edit/closebrackets", 
				 "./sapl-mode", "./xtext-codemirror"], function(addon1, addon2, mode, xtext) {
			_this.editor = xtext.createEditor({
				document: 					_this.shadow,
				xtextLang : 				"sapl",
				sendFullText : 				true,
				syntaxDefinition: 			mode,
				lineNumbers:  				true,
				showCursorWhenSelecting: 	true,
				autoCloseBrackets:			true,
				matchBrackets:				true,
				enableValidationService:	true
			});
			if(!_this.document) {
				_this.setDocument("");
			}
			_this.editor.doc.setValue(_this.document);
			_this.editor.doc.on("change", function(doc, changeObj) {
				_this.setDocument(doc.getValue());
			});
		});
	}
	
	setDocument(document) {
		this.setAttribute('document', document);		
	}
	
	set document(document) {
		this.setDocument(document);
		if(this.editor) {
			this.editor.doc.setValue(document);
		}
	}
	
	get document() {
		return this.getAttribute('document');
	}

	static get observedAttributes() {
		return ['document'];
	}

	attributeChangedCallback(attrName, oldVal, newVal) {
	    switch (attrName) {
	      case 'document':
  	    	  this.dispatchEvent(new Event("document-changed"));
	    }
	}
	  
	/*
	 * The codemirror.css and show-hint.css are a manual copy from the CodeMirror package.
	 * Need to be updated when updating CodeMirror NPM.
	 */
	static template () {
		    return `
<link rel="stylesheet" type="text/css" href="./styles/codemirror.css">
<link rel="stylesheet" type="text/css" href="./styles/show-hint.css">
<link rel="stylesheet" type="text/css" href="./styles/xtext-codemirror.css">
<link rel="stylesheet" type="text/css" href="./styles/sapl-text-area-style.css">
<div id="xtext-editor" data-editor-xtext-lang="sapl"/>
		      `;
		  }
}

window.customElements.define('sapl-editor', SAPLEditor);