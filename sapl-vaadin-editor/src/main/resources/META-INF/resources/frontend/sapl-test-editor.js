import { LitElement, html } from 'lit';
import { CodeMirrorStyles, CodeMirrorLintStyles, CodeMirrorHintStyles, XTextAnnotationsStyles, AutocompleteWidgetStyle, ReadOnlyStyle, HeightFix, DarkStyle } from './shared-styles.js';
import "./sapl-test-mode";
import { exports } from "./xtext-codemirror";

class SAPLTestEditor extends LitElement {

  constructor() {
    super();
    this.document = "";
    this.xtextLang = "sapltest";
  }

  static get properties() {
    return {
      document: { type: String },
      isReadOnly: { type: Boolean },
      hasLineNumbers: { type: Boolean },
      autoCloseBrackets: { type: Boolean },
      matchBrackets: { type: Boolean },
      textUpdateDelay: { type: Number },
      editor: { type: Object },
      xtextLang: { type: String },
      isLint: { type: Boolean },
      isDarkTheme: { type: Boolean }
    }
  }

  static get styles() {
    return [
      CodeMirrorStyles,
      CodeMirrorLintStyles,
      CodeMirrorHintStyles,
      XTextAnnotationsStyles,
      AutocompleteWidgetStyle,
      ReadOnlyStyle,
      HeightFix,
      DarkStyle
    ]
  }

  set editor(value) {
    let oldVal = this._editor;
    this._editor = value;
    console.debug('SaplTestEditor: set editor', oldVal, value);
    this.requestUpdate('editor', oldVal);
    this.onEditorChangeCheckOptions(value);
  }

  get editor() {
    return this._editor;
  }

  set isReadOnly(value) {
    let oldVal = this._isReadOnly;
    this._isReadOnly = value;
    console.debug('SaplTestEditor: set isReadOnly', oldVal, value);
    this.requestUpdate('isReadOnly', oldVal);
    this.setEditorOption('readOnly', value);
  }

  get isReadOnly() { 
    return this._isReadOnly; 
  }

  set isLint(value) {
    let oldVal = this._isLint;
    this._isLint = value;
    console.debug('SaplTestEditor: set isLint', oldVal, value);
    this.requestUpdate("isLint", oldVal);
    this.setLintEditorOption(value);
  }

  get isLint() {
    return this._isLint;
  }

  set isDarkTheme(value) {
    let oldVal = this._isDarkTheme;
    this._isDarkTheme = value;
    console.debug('SaplTestEditor: set isDarkTheme', oldVal, value);
    this.requestUpdate("isDarkTheme", oldVal);
    this.setDarkThemeEditorOption(value);
  }

  get isDarkTheme() {
    return this._isDarkTheme;
  }

  firstUpdated(changedProperties) {
    var self = this;
    var shadowRoot = self.shadowRoot;

    var widget_container = document.createElement("div");
    widget_container.id = "widgetContainer";
    shadowRoot.appendChild(widget_container);

    self.editor = exports.createEditor({
      document: shadowRoot,
      xtextLang: self.xtextLang,
      sendFullText: true,
      syntaxDefinition: "xtext/sapltest",
      readOnly: false,
      lineNumbers: self.hasLineNumbers,
      showCursorWhenSelecting: true,
      enableValidationService: self.isLint,
      textUpdateDelay: self.textUpdateDelay,
      gutters: ["CodeMirror-lint-markers"],
      extraKeys: {"Ctrl-Space": "autocomplete"},
      hintOptions: {
        container: widget_container
      },
      theme: "default"
    });

    self.editor.doc.setValue(self.document);
    self.editor.doc.on("change", function (doc, changeObj) {
      var value = doc.getValue();
      self.onDocumentChanged(value);
    });

    self.registerValidationCallback(self.editor);
  }

  registerValidationCallback(editor) {
    var self = this;

    var xTextServices = editor.xtextServices;
    xTextServices.originalValidate = xTextServices.validate;
    xTextServices.validate = function (addParam) {
      var services = this;
      return services.originalValidate(addParam).done(function (result) {
        if(self.$server !== undefined) {
          var issues = result.issues;
          self.$server.onValidation(issues);
        }
        else {
          throw "Connection between editor and server could not be established. (onValidation)";
        }
      });
    }
  }

  onDocumentChanged(value) {
    this.document = value;
    if(this.$server !== undefined) {
      this.$server.onDocumentChanged(value);
    }
    else {
      throw "Connection between editor and server could not be established. (onDocumentChanged)";
    }
  }

  setEditorDocument(element, document) {
    this.document = document;
    if(element.editor !== undefined) {
      element.editor.doc.setValue(document);
    }
  }

  setEditorOption(option, value) {
    let isEditorSet = this.editor !== undefined;
    console.debug('SaplTestEditor: setEditorOption', option, value, isEditorSet);

    if(this.editor !== undefined) {
      if(option === 'readOnly') {
        if(value === true) {
          this.editor.setOption("theme", 'readOnly');
        } else {
          this.editor.setOption("theme", 'default');
        }
      }
      this.editor.setOption(option, value);  
    }
  }

  scrollToBottom() {
    let isEditorSet = this.editor !== undefined;
    console.debug('SaplTestEditor: scrollToBottom', isEditorSet);

    let scrollInfo = this.editor.getScrollInfo();
    if(isEditorSet) {
      this.editor.scrollTo(null, scrollInfo.height);
    }
  }

  setDarkThemeEditorOption(value) {
    console.debug('SaplTestEditor: setDarkThemeEditorOption', value);
    let isEditorSet = this.editor !== undefined;
    if(isEditorSet) {
      if(value === true) {
        this.editor.setOption("theme", 'dracula');
      } else {
        // Needed to not overwrite readonly Theme
        this.setEditorOption('readOnly', this._isReadOnly);
      }
    }
  }

  setLintEditorOption(value) {
    console.debug('SaplTestEditor: setLintOption', value);
    let isEditorSet = this.editor !== undefined;
    if(isEditorSet) {
      this.editor.setOption("enableValidationService", value);
    }
  }

  onEditorChangeCheckOptions(editor) {
    let isEditorSet = editor !== undefined;
    console.debug('SaplTestEditor: onEditorChangeCheckOptions', isEditorSet);

    if(isEditorSet) {
      this.setEditorOption('readOnly', this.isReadOnly);
      this.setDarkThemeEditorOption(this.isDarkTheme);
      this.setLintEditorOption(this.isLint);
    }
  }

  render() {
    return html`
<div id="xtext-editor" data-editor-xtext-lang="${this.xtextLang}"/>
		      `;
  }
}

customElements.define('sapl-test-editor', SAPLTestEditor);