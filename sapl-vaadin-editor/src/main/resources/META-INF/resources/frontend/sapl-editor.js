import { LitElement, html, css } from 'lit';
import {
    CodeMirrorStyles,
    CodeMirrorLintStyles,
    CodeMirrorHintStyles,
    XTextAnnotationsStyles,
    AutocompleteWidgetStyle,
    ReadOnlyStyle,
    HeightFix,
    DarkStyle
} from './shared-styles.js';

import './sapl-mode';
import { exports as XtextCm } from './xtext-codemirror-patched.js';

import codemirror from 'codemirror';
import 'codemirror/addon/lint/lint';
import 'codemirror/addon/hint/show-hint';
import 'codemirror/addon/merge/merge';
import * as DMP from 'diff-match-patch';

export let saplPdpConfigurationId = null;

const MergeSizing = css`
    :host { display:block; height:100%; }
    #host { height:100%; display:flex; flex-direction:column; }
    #body { flex:1 1 auto; min-height:0; position:relative; }
    #xtext-wrap, #merge-wrap { position:absolute; inset:0; }
    #xtext-wrap { display:flex; }
    #xtext-editor { flex:1 1 auto; min-height:0; }
    #merge-root, .CodeMirror-merge, .CodeMirror { height:100%; }
`;

const MergeLayout = css`
    .CodeMirror-merge{ position:relative; height:100%; white-space:pre; }
    .CodeMirror-merge, .CodeMirror-merge .CodeMirror{ height:100%; }
    .CodeMirror-merge-2pane .CodeMirror-merge-pane{ width:47%; }
    .CodeMirror-merge-2pane .CodeMirror-merge-gap{ width:6%; }
    .CodeMirror-merge-3pane .CodeMirror-merge-pane{ width:31%; }
    .CodeMirror-merge-3pane .CodeMirror-merge-gap{ width:3.5%; }
    .CodeMirror-merge-pane{ display:inline-block; white-space:normal; vertical-align:top; height:100%; box-sizing:border-box; }
    .CodeMirror-merge-pane-rightmost{ position:absolute; right:0; z-index:1; }
    .CodeMirror-merge-gap{ z-index:2; display:inline-block; height:100%; box-sizing:border-box; overflow:hidden; position:relative; }
`;

const MergeControls = css`
    .CodeMirror-merge-scrolllock-wrap{ position:absolute; bottom:0; left:50%; }
    .CodeMirror-merge-scrolllock{ position:relative; left:-50%; cursor:pointer; color:var(--sapl-merge-arrow,#378b8a); line-height:1; }
    .CodeMirror-merge-scrolllock:after{ content:"\\21db\\00a0\\00a0\\21da"; }
    .CodeMirror-merge-scrolllock.CodeMirror-merge-scrolllock-enabled:after{ content:"\\21db\\21da"; }
    .CodeMirror-merge-copybuttons-left,.CodeMirror-merge-copybuttons-right{ position:absolute; left:0; top:0; right:0; bottom:0; line-height:1; }
    .CodeMirror-merge-copy,.CodeMirror-merge-copy-reverse{ position:absolute; cursor:pointer; color:var(--sapl-merge-arrow,#378b8a); z-index:3; }
    .CodeMirror-merge-copybuttons-left .CodeMirror-merge-copy{ left:2px; }
    .CodeMirror-merge-copybuttons-right .CodeMirror-merge-copy{ right:2px; }
`;

const MergeColors = css`
    .CodeMirror-merge-l-connect,.CodeMirror-merge-r-connect{
        fill:var(--sapl-merge-connector,#252a2e);
        stroke:var(--sapl-merge-connector,#252a2e);
        stroke-width:1px;
        opacity:1;
    }
    .CodeMirror-merge-gap .CodeMirror-merge-copy,
    .CodeMirror-merge-gap .CodeMirror-merge-copy-reverse,
    .CodeMirror-merge-gap .CodeMirror-merge-scrolllock,
    .CodeMirror-merge-gap .CodeMirror-merge-scrolllock::after{
        color:var(--sapl-merge-arrow,#378b8a) !important;
    }
`;

const ChangeMarkers = css`
    .cm-merge-chunk-line{ background: rgba(255,200,0,.15); }
    .cm-merge-gutter-marker{ width:8px; height:8px; border-radius:50%; display:inline-block; }
    .cm-merge-gutter-marker.changed{ background:#f39c12; }
`;

const DiffMatchPatch = DMP.default || DMP.diff_match_patch || DMP;
const DIFF_DELETE = DMP.DIFF_DELETE ?? -1;
const DIFF_INSERT = DMP.DIFF_INSERT ??  1;
const DIFF_EQUAL  = DMP.DIFF_EQUAL  ??  0;

class SAPLEditor extends LitElement {
    static get properties(){
        return {
            document: { type:String },
            isReadOnly: { type:Boolean },
            hasLineNumbers: { type:Boolean },
            textUpdateDelay: { type:Number },
            xtextLang: { type:String },
            isLint: { type:Boolean },
            configurationId: { type:String },
            isDarkTheme: { type:Boolean },
            mergeEnabled: { type:Boolean }
        };
    }

    static get styles(){
        return [
            CodeMirrorStyles, CodeMirrorLintStyles, CodeMirrorHintStyles,
            XTextAnnotationsStyles, AutocompleteWidgetStyle,
            ReadOnlyStyle, HeightFix, DarkStyle,
            MergeSizing, MergeLayout, MergeControls, MergeColors, ChangeMarkers
        ];
    }

    constructor(){
        super();
        this.document = '';
        this.xtextLang = 'sapl';
        this.isReadOnly = false;
        this.hasLineNumbers = true;
        this.textUpdateDelay = 0;
        this.isLint = true;
        this.isDarkTheme = false;
        this.configurationId = null;

        this.mergeEnabled = false;

        this._editor = undefined;     // Xtext editor
        this._mergeView = undefined;  // MergeView
        this._rightMergeText = '';

        this._mergeOptions = {
            revertButtons: true,
            showDifferences: true,
            connect: null,
            collapseIdentical: false,
            allowEditingOriginals: false,
            ignoreWhitespace: false
        };

        this._changeMarkersEnabled = true;
        this._chunkList = [];
        this._gutterId = 'merge-changes';
        this._appliedLineClassesLeft = [];
        this._appliedLineClassesRight = [];
        this._appliedGutterMarkersLeft = [];
        this._appliedGutterMarkersRight = [];
        this._recalcDebounce = null;

        this._mainScrollHandler = null;
        this._rightScrollHandler = null;
    }

    set configurationId(v){
        this._configurationId = v ?? null;
        saplPdpConfigurationId = this._configurationId;
        this.requestUpdate();
    }
    get configurationId(){ return this._configurationId; }

    render(){
        return html`
      <div id="host">
        <div id="body">
          <div id="xtext-wrap" style="display:flex">
            <div id="xtext-editor" data-editor-xtext-lang="${this.xtextLang}"></div>
          </div>
          <div id="merge-wrap" style="display:none">
            <div id="merge-root"></div>
          </div>
        </div>
      </div>
    `;
    }

    firstUpdated(){
        if (typeof window.diff_match_patch === 'undefined') {
            window.diff_match_patch = DiffMatchPatch;
            window.DIFF_DELETE = DIFF_DELETE;
            window.DIFF_INSERT = DIFF_INSERT;
            window.DIFF_EQUAL  = DIFF_EQUAL;
        }
        this._createXtextOnce(this.document);
        if (this.mergeEnabled) this._enterMerge(); else this._enterXtext();
        this._applyThemeVars();
        this._applyTheme();
    }

    disconnectedCallback(){
        super.disconnectedCallback?.();
        this._detachScrollHandlers();
        this._destroyMerge(); // Xtext kept alive; safe to leave
    }

    /* ----------------- DOM refs ----------------- */
    _xWrap(){ return this.shadowRoot.getElementById('xtext-wrap'); }
    _mWrap(){ return this.shadowRoot.getElementById('merge-wrap'); }
    _mRoot(){ return this.shadowRoot.getElementById('merge-root'); }

    /* ----------------- Mode switching ----------------- */
    setMergeModeEnabled(enabled){
        const want = !!enabled;
        if (want === this.mergeEnabled) return;
        this.mergeEnabled = want;
        if (this.mergeEnabled) this._enterMerge(); else this._enterXtext();
    }

    _enterXtext(){
        // hide merge, destroy it; show xtext (create once)
        this._destroyMerge();
        this._mWrap().style.display = 'none';
        this._xWrap().style.display = 'flex';
        // ensure options propagate to xtext
        this._applyTheme();
        this._applyLint();
        // keep current left doc (from merge if present)
        if (this._editor && typeof this._editor.refresh === 'function') this._editor.refresh();
    }

    _enterMerge(){
        // snapshot left text from xtext editor (if present)
        const leftText = this._editor?.doc?.getValue?.() ?? this.document ?? '';
        this._xWrap().style.display = 'none';
        this._mWrap().style.display = 'block';
        this._createMerge(leftText, this._rightMergeText ?? '');
    }

    /* ----------------- Xtext editor ----------------- */
    _createXtextOnce(value){
        if (this._editor) {
            this._editor.doc.setValue(value ?? '');
            return;
        }
        // Create once against #xtext-editor; Xtext locates it via data-editor-xtext-lang
        const widgetContainer = document.createElement('div');
        widgetContainer.id = 'widgetContainer';
        this.shadowRoot.appendChild(widgetContainer);

        this._editor = XtextCm.createEditor({
            document: this.shadowRoot,
            xtextLang: this.xtextLang,
            sendFullText: true,
            syntaxDefinition: 'xtext/sapl',
            readOnly: false,
            lineNumbers: this.hasLineNumbers,
            showCursorWhenSelecting: true,
            enableValidationService: this.isLint,
            textUpdateDelay: this.textUpdateDelay,
            gutters: ['CodeMirror-lint-markers'],
            extraKeys: { 'Ctrl-Space': 'autocomplete' },
            hintOptions: { container: widgetContainer, updateOnCursorActivity: false },
            theme: 'default'
        });

        this._editor.doc.setValue(value ?? '');
        this._editor.doc.on('change', (doc) => this.onDocumentChanged(doc.getValue()));
        this._registerValidationCallback(this._editor);
        this._applyTheme();
    }

    _registerValidationCallback(editor){
        const self = this;
        const xs = editor.xtextServices;
        xs.originalValidate = xs.validate;
        xs.validate = function(addParam){
            const services = this;
            return services.originalValidate(addParam).done(function(result){
                if (self.$server !== undefined){
                    self.$server.onValidation(result.issues);
                } else {
                    throw 'Connection between editor and server could not be established. (onValidation)';
                }
            });
        };
    }

    /* ----------------- Merge view ----------------- */
    _createMerge(leftValue, rightValue){
        this._destroyMerge();

        const root = this._mRoot();
        this._mergeView = codemirror.MergeView(root, {
            value: leftValue ?? '',
            origLeft: null,
            origRight: rightValue ?? '',
            lineNumbers: this.hasLineNumbers,
            mode: 'xtext/sapl',
            readOnly: this.isReadOnly, // left readOnly follows component
            allowEditingOriginals: this._mergeOptions.allowEditingOriginals,
            showDifferences: this._mergeOptions.showDifferences,
            revertButtons: this._mergeOptions.revertButtons,
            connect: this._mergeOptions.connect,
            collapseIdentical: this._mergeOptions.collapseIdentical,
            gutters: ['CodeMirror-lint-markers'],
            theme: this._themeName()
        });

        const main = this._mergeView.edit;
        main.setOption('readOnly', this.isReadOnly);
        main.on('change', () => this.onDocumentChanged(main.getValue()));
        this._mainScrollHandler = () => this._scheduleRecalc();
        main.getScrollerElement().addEventListener('scroll', this._mainScrollHandler);

        const right = this._getRightEditor();
        if (right){
            right.setOption('readOnly', !this._mergeOptions.allowEditingOriginals);
            right.setOption('lineNumbers', this.hasLineNumbers);
            right.setOption('mode','xtext/sapl');
            right.setOption('theme', this._themeName());
            right.on('change', () => this._scheduleRecalc());
            this._rightScrollHandler = () => this._scheduleRecalc();
            right.getScrollerElement().addEventListener('scroll', this._rightScrollHandler);
        }

        this._applyTheme(); // updates CSS vars/colors
        this._scheduleRecalc();
    }

    _destroyMerge(){
        this._detachScrollHandlers();
        const root = this._mRoot();
        if (root) root.innerHTML = '';
        this._mergeView = undefined;
        this._clearChangeMarkers();
        this._chunkList = [];
    }

    _detachScrollHandlers(){
        const main = this._getMainEditor();
        const right = this._getRightEditor();
        if (main && this._mainScrollHandler) main.getScrollerElement().removeEventListener('scroll', this._mainScrollHandler);
        if (right && this._rightScrollHandler) right.getScrollerElement().removeEventListener('scroll', this._rightScrollHandler);
        this._mainScrollHandler = null;
        this._rightScrollHandler = null;
    }

    _getMainEditor(){ return this._mergeView ? this._mergeView.edit : this._editor; }
    _getRightEditor(){
        if (!this._mergeView || !this._mergeView.right) return undefined;
        return this._mergeView.right.orig;
    }

    /* ----------------- Options & theming ----------------- */
    setEditorDocument(_el, doc){
        this.document = doc;
        const main = this._getMainEditor();
        if (main) (main.doc ? main.doc.setValue(doc) : main.setValue(doc));
        if (this._editor && main !== this._editor) this._editor.doc.setValue(doc); // keep Xtext in sync when hidden
        this._scheduleRecalc();
    }

    setEditorOption(option, value){
        if (option === 'readOnly'){ this.isReadOnly = !!value; return; }
        const main = this._getMainEditor();
        if (main) main.setOption(option, value);
        const right = this._getRightEditor();
        if (right && (option==='lineNumbers' || option==='mode')) right.setOption(option, value);
        this._scheduleRecalc();
    }

    setDarkThemeEditorOption(v){ this.isDarkTheme = !!v; }
    setLintEditorOption(v){ this.isLint = !!v; if (!this.mergeEnabled && this._editor) this._editor.setOption('enableValidationService', this.isLint); }

    _themeName(){
        if (this.isReadOnly) return this.isDarkTheme ? 'dracularo' : 'readOnly';
        return this.isDarkTheme ? 'dracula' : 'default';
    }

    _applyTheme(){
        const main = this._getMainEditor();
        if (main) main.setOption('theme', this._themeName());
        const right = this._getRightEditor();
        if (right) right.setOption('theme', this._themeName());
        this._applyThemeVars();
        this._scheduleRecalc();
    }

    _applyThemeVars(){
        const isDark = this.isDarkTheme === true;
        const connector = isDark ? '#252a2e' : '#c7d1d6';
        const arrow     = isDark ? '#5ac8c7' : '#378b8a';
        this.style.setProperty('--sapl-merge-connector', connector);
        this.style.setProperty('--sapl-merge-arrow', arrow);
    }

    _applyLint(){
        if (!this.mergeEnabled && this._editor){
            this._editor.setOption('enableValidationService', this.isLint);
        }
    }

    /* ----------------- Merge API parity ----------------- */
    setMergeRightContent(content){
        this._rightMergeText = content ?? '';
        const right = this._getRightEditor();
        if (right) right.setValue(this._rightMergeText);
        this._scheduleRecalc();
    }

    setMergeOption(option, value){
        if (option === 'revertButtons') this._mergeOptions.revertButtons = !!value;
        else if (option === 'showDifferences') this._mergeOptions.showDifferences = !!value;
        else if (option === 'connect') this._mergeOptions.connect = value;
        else if (option === 'collapseIdentical') this._mergeOptions.collapseIdentical = !!value;
        else if (option === 'allowEditingOriginals') this._mergeOptions.allowEditingOriginals = !!value;
        else if (option === 'ignoreWhitespace') this._mergeOptions.ignoreWhitespace = !!value;
        else return;

        if (this.mergeEnabled){
            const left = this._getMainEditor().getValue();
            const right = this._getRightEditor() ? this._getRightEditor().getValue() : this._rightMergeText;
            this._createMerge(left, right);
        }
    }

    enableChangeMarkers(enabled){
        this._changeMarkersEnabled = !!enabled;
        this._applyChangeMarkers();
    }

    nextChange(){ const m=this._getMainEditor(); if (m) m.execCommand('goNextDiff'); }
    prevChange(){ const m=this._getMainEditor(); if (m) m.execCommand('goPrevDiff'); }

    /* ----------------- Chunks & markers ----------------- */
    _scheduleRecalc(){ if (this._recalcDebounce) clearTimeout(this._recalcDebounce); this._recalcDebounce = setTimeout(()=>this._recalc(), 30); }

    _recalc(){
        if (!this._mergeView){ this._chunkList=[]; this._emitChunks(); this._applyChangeMarkers(); return; }
        let raw = [];
        try {
            raw = this._mergeView.rightChunks ? (this._mergeView.rightChunks() || [])
                : (this._mergeView.leftChunks ? (this._mergeView.leftChunks() || []) : []);
        } catch { raw = []; }
        const chunks = raw.map(ch => ({
            left:  { fromLine: ch.editFrom, toLine: ch.editTo - 1 },
            right: { fromLine: ch.origFrom, toLine: ch.origTo - 1 }
        }));
        chunks.sort((a,b)=>a.left.fromLine-b.left.fromLine || a.left.toLine-b.left.toLine);
        this._chunkList = chunks;
        this._emitChunks();
        this._applyChangeMarkers();
    }

    _emitChunks(){
        const compact = this._chunkList.map(c => ({ fromLine: c.left.fromLine, toLine: c.left.toLine }));
        this.dispatchEvent(new CustomEvent('sapl-merge-chunks', { bubbles:true, composed:true, detail:{ chunks: compact }}));
    }

    _applyChangeMarkers(){
        this._clearChangeMarkers();
        if (!this._changeMarkersEnabled || this._chunkList.length===0) return;
        const left = this._getMainEditor(); if (!left) return;
        const right = this._getRightEditor();

        const ensureGutter = (ed)=>{
            const cur = ed.getOption('gutters') || [];
            const set = new Set(cur);
            if (!set.has(this._gutterId)) { set.add(this._gutterId); ed.setOption('gutters', Array.from(set)); }
        };
        ensureGutter(left); if (right) ensureGutter(right);

        this._chunkList.forEach(ch=>{
            for (let ln = ch.left.fromLine; ln <= ch.left.toLine; ln++){
                left.addLineClass(ln,'wrap','cm-merge-chunk-line');
                this._appliedLineClassesLeft.push({ line: ln });
            }
            const lm = document.createElement('span'); lm.className='cm-merge-gutter-marker changed';
            left.setGutterMarker(ch.left.fromLine, this._gutterId, lm);
            this._appliedGutterMarkersLeft.push({ line: ch.left.fromLine });

            if (right && ch.right){
                for (let ln = ch.right.fromLine; ln <= ch.right.toLine; ln++){
                    right.addLineClass(ln,'wrap','cm-merge-chunk-line');
                    this._appliedLineClassesRight.push({ line: ln });
                }
                const rm = document.createElement('span'); rm.className='cm-merge-gutter-marker changed';
                right.setGutterMarker(ch.right.fromLine, this._gutterId, rm);
                this._appliedGutterMarkersRight.push({ line: ch.right.fromLine });
            }
        });
    }

    _clearChangeMarkers(){
        const left = this._getMainEditor();
        const right = this._getRightEditor();
        if (left){
            this._appliedLineClassesLeft.forEach(e=>{ try{ left.removeLineClass(e.line,'wrap','cm-merge-chunk-line'); }catch{} });
            this._appliedGutterMarkersLeft.forEach(e=>{ try{ left.setGutterMarker(e.line,this._gutterId,null); }catch{} });
        }
        if (right){
            this._appliedLineClassesRight.forEach(e=>{ try{ right.removeLineClass(e.line,'wrap','cm-merge-chunk-line'); }catch{} });
            this._appliedGutterMarkersRight.forEach(e=>{ try{ right.setGutterMarker(e.line,this._gutterId,null); }catch{} });
        }
        this._appliedLineClassesLeft = [];
        this._appliedLineClassesRight = [];
        this._appliedGutterMarkersLeft = [];
        this._appliedGutterMarkersRight = [];
    }

    /* ----------------- Server glue ----------------- */
    onDocumentChanged(value){
        this.document = value;
        if (this.$server !== undefined){
            this.$server.onDocumentChanged(value);
        } else {
            throw 'Connection between editor and server could not be established. (onDocumentChanged)';
        }
    }
}

customElements.define('sapl-editor', SAPLEditor);
