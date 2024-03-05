import { LitElement, html } from 'lit';
import { CodeMirrorStyles, CodeMirrorLintStyles, CodeMirrorHintStyles, HeightFix, ReadOnlyStyle, DarkStyle } from './shared-styles.js';
import codemirror from 'codemirror';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/addon/lint/lint';
import 'codemirror/addon/lint/json-lint';
import * as jsonlint from 'jsonlint-webpack';

class JSONEditor extends LitElement {

  constructor() {
    super();
    this.document = "";
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
      isLint: { type: Boolean },
      isDarkTheme: { type: Boolean }
    }
  }

  static get styles() {
    return [
      CodeMirrorStyles,
      CodeMirrorLintStyles,
      CodeMirrorHintStyles,
      HeightFix,
      ReadOnlyStyle,
      DarkStyle
    ]
  }

  set editor(value) {
    let oldVal = this._editor;
    this._editor = value;
    console.debug('JsonEditor: set editor', oldVal, value);
    this.requestUpdate('editor', oldVal);
    this.onEditorChangeCheckOptions(value);
  }

  get editor() {
    return this._editor;
  }

  set isReadOnly(value) {
    let oldVal = this._isReadOnly;
    this._isReadOnly = value;
    console.debug('JsonEditor: set isReadOnly', oldVal, value);
    this.requestUpdate('isReadOnly', oldVal);
    this.setEditorOption('readOnly', value);
  }

  get isReadOnly() {
    return this._isReadOnly;
  }

  set isLint(value) {
    let oldVal = this._isLint;
    this._isLint = value;
    console.debug('JsonEditor: set isLint', oldVal, value);
    this.requestUpdate("isLint", oldVal);
    this.setLintEditorOption(value);
  }

  get isLint() {
    return this._isLint;
  }

  set isDarkTheme(value) {
    let oldVal = this._isDarkTheme;
    this._isDarkTheme = value;
    console.debug('JsonEditor: set isDarkTheme', oldVal, value);
    this.requestUpdate("isDarkTheme", oldVal);
    this.setDarkThemeEditorOption(value);
  }

  get isDarkTheme() {
    return this._isDarkTheme;
  }

  firstUpdated(changedProperties) {
    window.jsonlint = jsonlint;

    var self = this;

    self.editor = codemirror(self.shadowRoot.querySelector('#json-editor'), {
      value: self.document,
      mode: "application/json",
      gutters: ["CodeMirror-lint-markers"],
      readOnly: self.isReadOnly,
      lineNumbers: self.hasLineNumbers,
      showCursorWhenSelecting: true,
      textUpdateDelay: self.textUpdateDelay,
      lint: {
        selfContain: true
      },
      theme: "default"
    });

    self.editor.on("change", function(cm, changeObj) {
      var value = cm.getValue();
      self.onDocumentChanged(value);
    });

    self.editor.on("mousedown", function(instance, event) {
      var line = instance.lineAtHeight(event.clientY, "client");
      var content = instance.getLine(line);
      console.log("User clicked on line " + (line + 1));
      self.onEditorClicked(line + 1, content);
    });
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

  onEditorClicked(value, content) {
    if(this.$server !== undefined) {
      this.$server.onEditorClicked(value, content);
    }
    else {
      throw "Connection between editor and server could not be established. (onEditorClicked)";
    }
  }

  // ServerCallable
  setEditorDocument(element, document) {
    this.document = document;
    if(element.editor !== undefined) {
      element.editor.doc.setValue(document);
    }
  }

  setEditorOption(option, value) {
    let isEditorSet = this.editor !== undefined;
    console.debug('JsonEditor: setEditorOption', option, value, isEditorSet);

    if(this.editor !== undefined) {
       if(option === 'readOnly') {
        if(value === true) {
        	if(this._isDarkTheme === true) {
          		this.editor.setOption("theme", 'dracularo');        
        	} else {
          		this.editor.setOption("theme", 'readOnly');
          	}
        } else if(this._isDarkTheme === true) {
        	this.editor.setOption("theme", 'dracula');        
        } else {
        	this.editor.setOption("theme", 'default');
        }        
      }
     this.editor.setOption(option, value);
    }
  }

  onEditorChangeCheckOptions(editor) {
    let isEditorSet = editor !== undefined;
    console.debug('JsonEditor: onEditorChangeCheckOptions', isEditorSet);

    if(isEditorSet) {
      this.setEditorOption('readOnly', this.isReadOnly);
      this.setDarkThemeEditorOption(this.isDarkTheme);
      this.setLintEditorOption(this.isLint);
    }
  }

  // ServerCallable
  onRefreshEditor() {
    let isEditorSet = this.editor !== undefined;
    console.debug('JsonEditor: onRefreshEditor', isEditorSet);

    if(isEditorSet) {
      console.debug('JsonEditor: refresh Editor');
      this.editor.refresh();
    }
  }

  render() {
    return html`<div id="json-editor"></div>`;
  }

  scrollToBottom() {
    let isEditorSet = this.editor !== undefined;
    console.debug('JsonEditor: scrollToBottom', isEditorSet);

    let scrollInfo = this.editor.getScrollInfo();
    if(isEditorSet) {
      this.editor.scrollTo(null, scrollInfo.height);
    }
  }

  appendText(text) {
    let isEditorSet = this.editor !== undefined;
    console.debug('JsonEditor: appendText', isEditorSet);
    if(isEditorSet) {
      let lines = this.editor.lineCount();
      this.editor.replaceRange(text + "\n", {line: lines});
    }
  }

  setDarkThemeEditorOption(value) {
    console.debug('JsonEditor: setDarkThemeEditorOption', value);
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
    console.debug('JsonEditor: setLintOption', value);
    let isEditorSet = this.editor !== undefined;
    if(isEditorSet) {
      if(value === true) {
        this.editor.setOption("lint", {selfContain: true});
      }
      else if (value === false) {
        this.editor.setOption("lint", false);
      }
    }
  }

}

customElements.define('json-editor', JSONEditor);