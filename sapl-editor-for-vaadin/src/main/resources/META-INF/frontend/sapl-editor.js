import { LitElement, html } from 'lit-element';

class SAPLEditor extends LitElement {

  constructor() {
    super();
    this.document = "";
    this.xtextLang = "sapl";
  }

  static get properties() {
    return {
      document: { type: String },
      hasLineNumbers: { type: Boolean },
      autoCloseBrackets: { type: Boolean },
      matchBrackets: { type: Boolean },
      xtextLang: { type: String },
      textUpdateDelay: { type: Number }
    }
  }

  firstUpdated(changedProperties) {
    this.$server.onFirstUpdated();
  }

  connectedCallback() {
    super.connectedCallback();

    var self = this;

    require(["codemirror/addon/edit/matchbrackets",
      "codemirror/addon/edit/closebrackets",
      "./sapl-mode", "./xtext-codemirror.min"], function (addon1, addon2, mode, xtext) {
        self.editor = xtext.createEditor({
          document: self.shadowRoot,
          xtextLang: self.xtextLang,
          sendFullText: true,
          syntaxDefinition: mode,
          lineNumbers: self.hasLineNumbers,
          showCursorWhenSelecting: true,
          autoCloseBrackets: self.autoCloseBrackets,
          matchBrackets: self.matchBrackets,
          enableValidationService: true,
          textUpdateDelay: self.textUpdateDelay
        });

        self.editor.doc.setValue(self.document);
        self.editor.doc.on("change", function (doc, changeObj) {
          var value = doc.getValue();
          self.onDocumentChanged(value);
        });
      });
  }

  onFirstUpdated(element) {
    console.log('onFirstUpdated');
    var self = this;
    var _services = element.editor.xtextServices;
    _services.originalValidate = _services.validate;

    _services.validate = function (addParam) {
      var services = this;
      return services.originalValidate(addParam).done(function (result) {
        var issues = result.issues;
        self.$server.onValidation(issues);
      });
    }
  }

  onDocumentChanged(value) {
    this.document = value;
    this.$server.onDocumentChanged(value);
  }

  setEditorDocument(element, document) {
    this.document = document;
    if(element.editor !== undefined) {
      element.editor.doc.setValue(document);
    }
  }

  render() {
    return html`
<style>
/* BASICS */

.CodeMirror {
  /* Set height, width, borders, and global font properties here */
  font-family: monospace;
  height: 300px;
  color: black;
  direction: ltr;
}

/* PADDING */

.CodeMirror-lines {
  padding: 4px 0; /* Vertical padding around content */
}
.CodeMirror pre.CodeMirror-line,
.CodeMirror pre.CodeMirror-line-like {
  padding: 0 4px; /* Horizontal padding of content */
}

.CodeMirror-scrollbar-filler, .CodeMirror-gutter-filler {
  background-color: white; /* The little square between H and V scrollbars */
}

/* GUTTER */

.CodeMirror-gutters {
  border-right: 1px solid #ddd;
  background-color: #f7f7f7;
  white-space: nowrap;
}
.CodeMirror-linenumbers {}
.CodeMirror-linenumber {
  padding: 0 3px 0 5px;
  min-width: 20px;
  text-align: right;
  color: #999;
  white-space: nowrap;
}

.CodeMirror-guttermarker { color: black; }
.CodeMirror-guttermarker-subtle { color: #999; }

/* CURSOR */

.CodeMirror-cursor {
  border-left: 1px solid black;
  border-right: none;
  width: 0;
}
/* Shown when moving in bi-directional text */
.CodeMirror div.CodeMirror-secondarycursor {
  border-left: 1px solid silver;
}
.cm-fat-cursor .CodeMirror-cursor {
  width: auto;
  border: 0 !important;
  background: #7e7;
}
.cm-fat-cursor div.CodeMirror-cursors {
  z-index: 1;
}
.cm-fat-cursor-mark {
  background-color: rgba(20, 255, 20, 0.5);
  -webkit-animation: blink 1.06s steps(1) infinite;
  -moz-animation: blink 1.06s steps(1) infinite;
  animation: blink 1.06s steps(1) infinite;
}
.cm-animate-fat-cursor {
  width: auto;
  border: 0;
  -webkit-animation: blink 1.06s steps(1) infinite;
  -moz-animation: blink 1.06s steps(1) infinite;
  animation: blink 1.06s steps(1) infinite;
  background-color: #7e7;
}
@-moz-keyframes blink {
  0% {}
  50% { background-color: transparent; }
  100% {}
}
@-webkit-keyframes blink {
  0% {}
  50% { background-color: transparent; }
  100% {}
}
@keyframes blink {
  0% {}
  50% { background-color: transparent; }
  100% {}
}

/* Can style cursor different in overwrite (non-insert) mode */
.CodeMirror-overwrite .CodeMirror-cursor {}

.cm-tab { display: inline-block; text-decoration: inherit; }

.CodeMirror-rulers {
  position: absolute;
  left: 0; right: 0; top: -50px; bottom: 0;
  overflow: hidden;
}
.CodeMirror-ruler {
  border-left: 1px solid #ccc;
  top: 0; bottom: 0;
  position: absolute;
}

/* DEFAULT THEME */

.cm-s-default .cm-header {color: blue;}
.cm-s-default .cm-quote {color: #090;}
.cm-negative {color: #d44;}
.cm-positive {color: #292;}
.cm-header, .cm-strong {font-weight: bold;}
.cm-em {font-style: italic;}
.cm-link {text-decoration: underline;}
.cm-strikethrough {text-decoration: line-through;}

.cm-s-default .cm-keyword {color: #708;}
.cm-s-default .cm-atom {color: #219;}
.cm-s-default .cm-number {color: #164;}
.cm-s-default .cm-def {color: #00f;}
.cm-s-default .cm-variable,
.cm-s-default .cm-punctuation,
.cm-s-default .cm-property,
.cm-s-default .cm-operator {}
.cm-s-default .cm-variable-2 {color: #05a;}
.cm-s-default .cm-variable-3, .cm-s-default .cm-type {color: #085;}
.cm-s-default .cm-comment {color: #a50;}
.cm-s-default .cm-string {color: #a11;}
.cm-s-default .cm-string-2 {color: #f50;}
.cm-s-default .cm-meta {color: #555;}
.cm-s-default .cm-qualifier {color: #555;}
.cm-s-default .cm-builtin {color: #30a;}
.cm-s-default .cm-bracket {color: #997;}
.cm-s-default .cm-tag {color: #170;}
.cm-s-default .cm-attribute {color: #00c;}
.cm-s-default .cm-hr {color: #999;}
.cm-s-default .cm-link {color: #00c;}

.cm-s-default .cm-error {color: #f00;}
.cm-invalidchar {color: #f00;}

.CodeMirror-composing { border-bottom: 2px solid; }

/* Default styles for common addons */

div.CodeMirror span.CodeMirror-matchingbracket {color: #0b0;}
div.CodeMirror span.CodeMirror-nonmatchingbracket {color: #a22;}
.CodeMirror-matchingtag { background: rgba(255, 150, 0, .3); }
.CodeMirror-activeline-background {background: #e8f2ff;}

/* STOP */

/* The rest of this file contains styles related to the mechanics of
   the editor. You probably shouldn't touch them. */

.CodeMirror {
  position: relative;
  overflow: hidden;
  background: white;
}

.CodeMirror-scroll {
  overflow: scroll !important; /* Things will break if this is overridden */
  /* 30px is the magic margin used to hide the element's real scrollbars */
  /* See overflow: hidden in .CodeMirror */
  margin-bottom: -30px; margin-right: -30px;
  padding-bottom: 30px;
  height: 100%;
  outline: none; /* Prevent dragging from highlighting the element */
  position: relative;
}
.CodeMirror-sizer {
  position: relative;
  border-right: 30px solid transparent;
}

/* The fake, visible scrollbars. Used to force redraw during scrolling
   before actual scrolling happens, thus preventing shaking and
   flickering artifacts. */
.CodeMirror-vscrollbar, .CodeMirror-hscrollbar, .CodeMirror-scrollbar-filler, .CodeMirror-gutter-filler {
  position: absolute;
  z-index: 6;
  display: none;
}
.CodeMirror-vscrollbar {
  right: 0; top: 0;
  overflow-x: hidden;
  overflow-y: scroll;
}
.CodeMirror-hscrollbar {
  bottom: 0; left: 0;
  overflow-y: hidden;
  overflow-x: scroll;
}
.CodeMirror-scrollbar-filler {
  right: 0; bottom: 0;
}
.CodeMirror-gutter-filler {
  left: 0; bottom: 0;
}

.CodeMirror-gutters {
  position: absolute; left: 0; top: 0;
  min-height: 100%;
  z-index: 3;
}
.CodeMirror-gutter {
  white-space: normal;
  height: 100%;
  display: inline-block;
  vertical-align: top;
  margin-bottom: -30px;
}
.CodeMirror-gutter-wrapper {
  position: absolute;
  z-index: 4;
  background: none !important;
  border: none !important;
}
.CodeMirror-gutter-background {
  position: absolute;
  top: 0; bottom: 0;
  z-index: 4;
}
.CodeMirror-gutter-elt {
  position: absolute;
  cursor: default;
  z-index: 4;
}
.CodeMirror-gutter-wrapper ::selection { background-color: transparent }
.CodeMirror-gutter-wrapper ::-moz-selection { background-color: transparent }

.CodeMirror-lines {
  cursor: text;
  min-height: 1px; /* prevents collapsing before first draw */
}
.CodeMirror pre.CodeMirror-line,
.CodeMirror pre.CodeMirror-line-like {
  /* Reset some styles that the rest of the page might have set */
  -moz-border-radius: 0; -webkit-border-radius: 0; border-radius: 0;
  border-width: 0;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  margin: 0;
  white-space: pre;
  word-wrap: normal;
  line-height: inherit;
  color: inherit;
  z-index: 2;
  position: relative;
  overflow: visible;
  -webkit-tap-highlight-color: transparent;
  -webkit-font-variant-ligatures: contextual;
  font-variant-ligatures: contextual;
}
.CodeMirror-wrap pre.CodeMirror-line,
.CodeMirror-wrap pre.CodeMirror-line-like {
  word-wrap: break-word;
  white-space: pre-wrap;
  word-break: normal;
}

.CodeMirror-linebackground {
  position: absolute;
  left: 0; right: 0; top: 0; bottom: 0;
  z-index: 0;
}

.CodeMirror-linewidget {
  position: relative;
  z-index: 2;
  padding: 0.1px; /* Force widget margins to stay inside of the container */
}

.CodeMirror-widget {}

.CodeMirror-rtl pre { direction: rtl; }

.CodeMirror-code {
  outline: none;
}

/* Force content-box sizing for the elements where we expect it */
.CodeMirror-scroll,
.CodeMirror-sizer,
.CodeMirror-gutter,
.CodeMirror-gutters,
.CodeMirror-linenumber {
  -moz-box-sizing: content-box;
  box-sizing: content-box;
}

.CodeMirror-measure {
  position: absolute;
  width: 100%;
  height: 0;
  overflow: hidden;
  visibility: hidden;
}

.CodeMirror-cursor {
  position: absolute;
  pointer-events: none;
}
.CodeMirror-measure pre { position: static; }

div.CodeMirror-cursors {
  visibility: hidden;
  position: relative;
  z-index: 3;
}
div.CodeMirror-dragcursors {
  visibility: visible;
}

.CodeMirror-focused div.CodeMirror-cursors {
  visibility: visible;
}

.CodeMirror-selected { background: #d9d9d9; }
.CodeMirror-focused .CodeMirror-selected { background: #d7d4f0; }
.CodeMirror-crosshair { cursor: crosshair; }
.CodeMirror-line::selection, .CodeMirror-line > span::selection, .CodeMirror-line > span > span::selection { background: #d7d4f0; }
.CodeMirror-line::-moz-selection, .CodeMirror-line > span::-moz-selection, .CodeMirror-line > span > span::-moz-selection { background: #d7d4f0; }

.cm-searching {
  background-color: #ffa;
  background-color: rgba(255, 255, 0, .4);
}

/* Used to force a border model for a node */
.cm-force-border { padding-right: .1px; }

@media print {
  /* Hide the cursor when printing */
  .CodeMirror div.CodeMirror-cursors {
    visibility: hidden;
  }
}

/* See issue #2901 */
.cm-tab-wrap-hack:after { content: ''; }

/* Help users use markselection to safely style text background */
span.CodeMirror-selectedtext { background: none; }

.CodeMirror-hints {
  position: absolute;
  z-index: 10;
  overflow: hidden;
  list-style: none;

  margin: 0;
  padding: 2px;

  -webkit-box-shadow: 2px 3px 5px rgba(0,0,0,.2);
  -moz-box-shadow: 2px 3px 5px rgba(0,0,0,.2);
  box-shadow: 2px 3px 5px rgba(0,0,0,.2);
  border-radius: 3px;
  border: 1px solid silver;

  background: white;
  font-size: 90%;
  font-family: monospace;

  max-height: 20em;
  overflow-y: auto;
}

.CodeMirror-hint {
  margin: 0;
  padding: 0 4px;
  border-radius: 2px;
  white-space: pre;
  color: black;
  cursor: pointer;
}

li.CodeMirror-hint-active {
  background: #08f;
  color: white;
}
.CodeMirror {
	height: 100%;
}
.annotations-gutter {
	width: 12px;
	background: #f0f0f0;
}
.xtext-annotation_error {
	width: 12px;
	height: 12px;
	 background-image: url(data:image/gif;base64,R0lGODlhDAAMAMZgAMo9NctAN89AQtFBR8pFPdJCR8tJQMtJQcxJQcxLQ8xLRM1MRc1NRc9RR81SSc1TStJTTdFVTc5XUNFXUM9YUdNYUNVZVNZeWNdfWthfXNRhWdlkX9pmYtxnZN1oZtxqZdNuZdNuZ91sZtNvad5sa9R2cOBzb9t2cOB1cuB2deF2c9V6dNZ7duF7eOJ8etx/e+N/fNiEgOOBfuSCgOODgtmIguWIh9mNh+aKidqOh+SPjNqSjeePjdyUjt+fm+Wem9+inuynpeGsqe2pp+2qqeGvrOysr+6xr+O4te61t+O6uPHCwefLyejOzOrT0vfY1/bZ2e3h4fjf3u3k4/ji4e3n5vrl5e3p6fvn5/zu7f3w8Pzy8v329v76+v77+/79/f///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////yH5BAEKAH8ALAAAAAAMAAwAAAeGgH+CU0U1KztOgopKDRoXGBUEPYpICEtbOCouVC8sf1EHHy5WVi5SVi0KSkARKio8YFhaMyobISUWJLpdXDS6HQkjAgUFRmBJxAUDAD4QHkRfUF0pHh4ZDEwGQWBDMFlWNioTOX8xP0euMk9eOg9Xf1UgCxwmKCInDk2Kf0IUAAESblQRFAgAOw==);
	background-repeat: no-repeat;
}
.xtext-annotation_warning {
	width: 12px;
	height: 12px;
    background-image: url(data:image/gif;base64,R0lGODlhDAAMAMZEAFAnAIpmMIlpOpNvNp96P6uRYMqMO8uMOs2QP9GVSNWbRtObT9acTMOhW9OeVtShW9qiV9ukU9ykVd6oXt2qaeGqYdqsat+tYOKtZdivdt+xcNy0e+W1buS2eOu9Z+nBgevDcevEdu3Fb+jGmujIn+PMie3JjPDMevLMeeTNre/PjebQsOjSs/HXmPjYgfjYhfnZgunYw/bci+raxfvchuvbx/nejfzejOndzv3ekffhnP7gkung1Ori2Ozj1uvk2u3r6O/t6f72xf/3x////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////yH5BAEKAH8ALAAAAAAMAAwAAAdngH+Cfz4jJEGDiX8YJh8dioIsHENCFTWQEy0CBSoaiisXJQAAOhIziRAnMgEDNiAUgykRMLS0LgwxghYiNDQNDb0eG4IJLzc3BATHKAuCDyE70dE5ChmCPAYI2toHDjiDQD3i4j+DgQA7);
	background-repeat: no-repeat;
}
.xtext-annotation_info {
	width: 12px;
	height: 12px;
    background-image: url(data:image/gif;base64,R0lGODlhDAAMAMIGAKm1/6u3/rfB+7jB+8/V9ufo8f///////yH5BAEKAAcALAAAAAAMAAwAAAMqeEoSIUSpMoC9o6h6+zhEZxgd051AI5KXs3bPe0GyFXHod1C5JjEOiCQBADs=);
	background-repeat: no-repeat;
}
.xtext-marker_error {
	z-index: 30;
	background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAADCAYAAAC09K7GAAAABmJLR0QA/wD/AP+gvaeTAAAAHElEQVQI12NggIL/DAz/GdA5/xkY/qPKMDAwAADLZwf5rvm+LQAAAABJRU5ErkJggg==");
	background-repeat: repeat-x;
	background-position: left bottom;
}
.xtext-marker_warning {
	z-index: 20;
	background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAADCAYAAAC09K7GAAAABmJLR0QA/wD/AP+gvaeTAAAAMklEQVQI12NkgIIvJ3QXMjAwdDN+OaEbysDA4MPAwNDNwMCwiOHLCd1zX07o6kBVGQEAKBANtobskNMAAAAASUVORK5CYII=");
	background-repeat: repeat-x;
	background-position: left bottom;
}
.xtext-marker_info {
	z-index: 10;
	background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAADCAYAAAC09K7GAAAABmJLR0QA/wD/AP+gvaeTAAAANklEQVQI12NkgIIVRx8tZGBg6GZccfRRKAMDgw8DA0M3AwPDIiYGBoZKBgaG7ghruSsMDAwpABH5CoqwzCoTAAAAAElFTkSuQmCC");
	background-repeat: repeat-x;
	background-position: left bottom;
}
.xtext-marker_read {
	background-color: #ddd;
}
.xtext-marker_write {
	background-color: yellow;
}
#xtext-editor {
	display: block;
	top: 0;
	bottom: 0;
	left: 0;
	right: 0;
	border: 1px solid #aaa;
}

div.CodeMirror span.CodeMirror-matchingbracket {
	color: #1b1af9;
	font-weight: bold;
}

div.CodeMirror span.CodeMirror-nonmatchingbracket {
	color: #ff2727;
	font-weight: bold;
}

.CodeMirror pre.CodeMirrot-line, .CodeMirror pre.CodeMirrot-line  {
	white-space: pre-wrap;
}

.status-wrapper {
	display: block;
    bottom: 0;
    left: 0;
    right: 0;
	height: 20px;
	margin-top: 10px;
}

#dirty-indicator {
	display: inline;
	color: #e8e8e8;
	padding: 1px 8px 1px 8px;
	border: 1px solid #ccc;
	margin-left: 10px;
}

#dirty-indicator.dirty {
	background-color: #88e;
}

#status {
	display: inline;
}
</style>
<div id="xtext-editor" data-editor-xtext-lang="${this.xtextLang}"/>
		      `;
  }
}

customElements.define('sapl-editor', SAPLEditor);