/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * JSON Editor using CodeMirror 6 with syntax highlighting, linting, and merge view.
 */
import { LitElement, html, css } from 'lit';
import { EditorView, basicSetup } from 'codemirror';
import { EditorState, Compartment } from '@codemirror/state';
import { keymap } from '@codemirror/view';
import { indentWithTab } from '@codemirror/commands';
import { oneDark } from '@codemirror/theme-one-dark';
import { json, jsonParseLinter } from '@codemirror/lang-json';
import { linter, lintGutter } from '@codemirror/lint';
import { bracketMatching } from '@codemirror/language';
import { closeBrackets } from '@codemirror/autocomplete';
import { MergeView } from '@codemirror/merge';

const themeCompartment = new Compartment();
const readOnlyCompartment = new Compartment();
const lintCompartment = new Compartment();
const bracketMatchingCompartment = new Compartment();
const closeBracketsCompartment = new Compartment();

class JsonEditorLsp extends LitElement {
    static properties = {
        document: { type: String },
        isDarkTheme: { type: Boolean },
        isReadOnly: { type: Boolean },
        hasLineNumbers: { type: Boolean },
        isLint: { type: Boolean },
        isMergeMode: { type: Boolean },
        mergeRightContent: { type: String },
        matchBrackets: { type: Boolean },
        autoCloseBrackets: { type: Boolean },
        highlightChanges: { type: Boolean },
        collapseUnchanged: { type: Boolean }
    };

    static styles = css`
        :host {
            display: block;
            height: 100%;
            width: 100%;
        }
        #editor-container {
            height: 100%;
            width: 100%;
            border: 1px solid var(--lumo-contrast-20pct, #ccc);
            border-radius: var(--lumo-border-radius-m, 4px);
            overflow: hidden;
        }
        .cm-editor {
            height: 100%;
        }
        /* Light theme background - applied via host attribute */
        :host([data-theme="light"]) .cm-editor {
            background-color: #ffffff !important;
            color: #000000 !important;
        }
        :host([data-theme="light"]) .cm-gutters {
            background-color: #f5f5f5 !important;
            color: #333333 !important;
            border-right: 1px solid #ddd !important;
        }
        :host([data-theme="light"]) .cm-activeLineGutter {
            background-color: #e0e0e0 !important;
        }
        :host([data-theme="light"]) .cm-activeLine {
            background-color: rgba(0, 0, 0, 0.05) !important;
        }
        :host([data-theme="light"]) .cm-content {
            caret-color: #000000 !important;
        }

        /* Read-only visual indicator */
        :host([data-readonly="true"]) .cm-editor {
            opacity: 0.7;
        }
        :host([data-readonly="true"]) .cm-content {
            cursor: not-allowed;
        }
        :host([data-readonly="true"][data-theme="light"]) .cm-editor {
            background-color: #f8f8f8 !important;
        }
        :host([data-readonly="true"][data-theme="dark"]) .cm-editor {
            background-color: #1e1e1e !important;
        }
        :host([data-readonly="true"]) .cm-gutters {
            opacity: 0.6;
        }
        :host([data-readonly="true"]) #editor-container {
            border-color: var(--lumo-contrast-10pct, #ddd);
        }

        .cm-scroller {
            overflow: auto;
        }

        /* CM6 MergeView styles */
        .cm-mergeView {
            height: 100% !important;
            width: 100% !important;
            display: block !important;
        }
        .cm-mergeViewEditors {
            height: 100% !important;
            width: 100% !important;
            display: flex !important;
            flex-direction: row !important;
        }
        .cm-mergeViewEditor {
            flex: 1 1 0 !important;
            min-width: 0 !important;
            height: 100% !important;
            overflow: hidden !important;
        }
        .cm-mergeViewEditor .cm-editor {
            height: 100% !important;
            width: 100% !important;
        }

        /* Revert controls container - the gap between editors */
        .cm-mergeViewEditors > .cm-merge-revert {
            flex: 0 0 40px !important;
            width: 40px !important;
            min-width: 40px !important;
            position: relative !important;
            background: var(--lumo-contrast-5pct, #f5f5f5) !important;
            border-left: 1px solid var(--lumo-contrast-20pct, #ccc) !important;
            border-right: 1px solid var(--lumo-contrast-20pct, #ccc) !important;
            overflow: visible !important;
        }

        /* Revert buttons inside the gap */
        .cm-mergeViewEditors > .cm-merge-revert > button {
            position: absolute !important;
            left: 50% !important;
            transform: translateX(-50%) !important;
            background: var(--lumo-primary-color, #1976d2) !important;
            color: white !important;
            border: none !important;
            border-radius: 3px !important;
            cursor: pointer !important;
            padding: 2px 8px !important;
            font-size: 14px !important;
            line-height: 1.2 !important;
            z-index: 10 !important;
        }
        .cm-mergeViewEditors > .cm-merge-revert > button:hover {
            background: var(--lumo-primary-color-50pct, #1565c0) !important;
        }

        /* Change gutter - shows markers for changed lines */
        .cm-changeGutter {
            width: 6px !important;
            min-width: 6px !important;
        }
        .cm-changedLineGutter {
            width: 6px !important;
            background: var(--lumo-primary-color, #1976d2) !important;
        }

        /* Spacer widgets for aligning editors at diff chunks */
        .cm-mergeSpacer {
            background: var(--lumo-contrast-5pct, #f5f5f5) !important;
        }

        /* Collapsed unchanged sections */
        .cm-collapsedLines {
            background: var(--lumo-contrast-5pct, #f5f5f5) !important;
            color: var(--lumo-contrast-50pct, #888) !important;
            cursor: pointer;
            padding: 2px 8px;
        }
        .cm-collapsedLines:hover {
            background: var(--lumo-contrast-10pct, #eee) !important;
        }

        /* Change highlighting */
        .cm-changedLine {
            background-color: rgba(255, 200, 0, 0.15) !important;
        }
        .cm-deletedLine {
            background-color: rgba(255, 100, 100, 0.2) !important;
        }
        .cm-insertedLine {
            background-color: rgba(100, 255, 100, 0.2) !important;
        }
        .cm-changedText {
            background-color: rgba(255, 200, 0, 0.35) !important;
        }

        /* Cursor visibility - use theme-appropriate colors */
        .cm-cursor,
        .cm-cursor-primary {
            border-left-width: 2px !important;
            border-left-style: solid !important;
            visibility: visible !important;
            display: block !important;
        }
        /* Dark theme cursor - bright cyan */
        :host([data-theme="dark"]) .cm-cursor,
        :host([data-theme="dark"]) .cm-cursor-primary {
            border-left-color: #00ffff !important;
        }
        /* Light theme cursor - dark blue */
        :host([data-theme="light"]) .cm-cursor,
        :host([data-theme="light"]) .cm-cursor-primary {
            border-left-color: #0000ff !important;
        }
        /* Ensure cursor layer is above other content */
        .cm-cursorLayer {
            z-index: 200 !important;
            pointer-events: none !important;
        }
        /* Merge view specific cursor fixes */
        .cm-mergeViewEditor .cm-cursorLayer {
            z-index: 200 !important;
            overflow: visible !important;
        }
        .cm-mergeViewEditor .cm-cursor,
        .cm-mergeViewEditor .cm-cursor-primary {
            visibility: visible !important;
            display: block !important;
        }
    `;

    constructor() {
        super();
        this.document = '';
        this.isDarkTheme = false;
        this.isReadOnly = false;
        this.hasLineNumbers = true;
        this.isLint = true;
        this.isMergeMode = false;
        this.mergeRightContent = '';
        this.matchBrackets = true;
        this.autoCloseBrackets = true;
        this.highlightChanges = true;
        this.collapseUnchanged = false;

        // Internal state
        this._editor = null;
        this._rightEditor = null;
        this._mergeView = null;
        this._isInternalUpdate = false;

        // CM6 MergeView options (real API)
        this._mergeOptions = {
            revertControls: 'b-to-a',      // 'a-to-b' | 'b-to-a' | null
            orientation: 'a-b',             // 'a-b' | 'b-a'
            gutter: true,                   // show change markers in gutter
            collapseUnchangedMargin: 3,     // lines to show around changes
            collapseUnchangedMinSize: 4     // minimum lines to collapse
        };

        // Scroll sync state
        this._scrollSyncEnabled = false;
        this._scrollSyncing = false;

        // Navigation state for diff chunks
        this._currentChunkIndex = -1;
    }

    render() {
        return html`<div id="editor-container"></div>`;
    }

    firstUpdated() {
        // Set initial attributes for CSS selectors
        this.setAttribute('data-theme', this.isDarkTheme ? 'dark' : 'light');
        this.setAttribute('data-readonly', this.isReadOnly ? 'true' : 'false');
        this._initEditor();
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this._destroyEditors();
    }

    _destroyEditors() {
        if (this._mergeView) {
            this._mergeView.destroy();
            this._mergeView = null;
            this._editor = null;
            this._rightEditor = null;
        } else {
            if (this._editor) {
                this._editor.destroy();
                this._editor = null;
            }
            if (this._rightEditor) {
                this._rightEditor.destroy();
                this._rightEditor = null;
            }
        }
    }

    _getBaseExtensions(withLinting = true) {
        const extensions = [
            basicSetup,
            keymap.of([indentWithTab]),
            json(),
            themeCompartment.of(this.isDarkTheme ? oneDark : []),
            readOnlyCompartment.of(EditorState.readOnly.of(this.isReadOnly)),
            bracketMatchingCompartment.of(this.matchBrackets ? bracketMatching() : []),
            closeBracketsCompartment.of(this.autoCloseBrackets ? closeBrackets() : [])
        ];

        if (withLinting && this.isLint) {
            extensions.push(lintCompartment.of([linter(jsonParseLinter()), lintGutter()]));
        } else {
            extensions.push(lintCompartment.of([]));
        }

        return extensions;
    }

    _initEditor() {
        const container = this.shadowRoot.getElementById('editor-container');
        if (!container) return;

        // Clear container
        container.innerHTML = '';

        if (this.isMergeMode) {
            this._initMergeView(container);
        } else {
            this._initSingleEditor(container);
        }
    }

    _initSingleEditor(container) {
        const extensions = [
            ...this._getBaseExtensions(true),
            EditorView.updateListener.of(update => {
                if (update.docChanged && !this._isInternalUpdate) {
                    this._onDocumentChanged(update.state.doc.toString());
                }
            })
        ];

        const state = EditorState.create({
            doc: this.document || '',
            extensions
        });

        this._editor = new EditorView({
            state,
            parent: container
        });
    }

    _initMergeView(container) {
        try {
            // Cursor visibility theme - ensures cursor is always visible in merge mode
            const cursorTheme = EditorView.theme({
                ".cm-cursor, .cm-cursor-primary": {
                    display: "block !important",
                    visibility: "visible !important",
                    borderLeftWidth: "2px",
                    borderLeftStyle: "solid",
                    borderLeftColor: this.isDarkTheme ? "#00ffff" : "#0000ff"
                },
                ".cm-cursorLayer": {
                    zIndex: "200 !important"
                }
            });

            // Base extensions for both editors (without read-only, added separately)
            const baseExtensions = [
                basicSetup,
                keymap.of([indentWithTab]),
                json(),
                themeCompartment.of(this.isDarkTheme ? oneDark : []),
                bracketMatchingCompartment.of(this.matchBrackets ? bracketMatching() : []),
                closeBracketsCompartment.of(this.autoCloseBrackets ? closeBrackets() : []),
                cursorTheme
            ];

            const lintExtensions = this.isLint ? [linter(jsonParseLinter()), lintGutter()] : [];

            // Build collapseUnchanged config if enabled
            const collapseConfig = this.collapseUnchanged ? {
                margin: this._mergeOptions.collapseUnchangedMargin,
                minSize: this._mergeOptions.collapseUnchangedMinSize
            } : undefined;

            // Create CM6 MergeView with real API options
            this._mergeView = new MergeView({
                a: {
                    doc: this.document || '',
                    extensions: [
                        ...baseExtensions,
                        ...lintExtensions,
                        readOnlyCompartment.of(EditorState.readOnly.of(this.isReadOnly)),
                        EditorView.updateListener.of(update => {
                            if (update.docChanged && !this._isInternalUpdate) {
                                this.document = update.state.doc.toString();
                                if (this.$server?.onDocumentChanged) {
                                    this.$server.onDocumentChanged(this.document);
                                }
                            }
                        })
                    ]
                },
                b: {
                    doc: this.mergeRightContent || '',
                    extensions: [
                        ...baseExtensions,
                        ...lintExtensions,
                        EditorState.readOnly.of(true)  // Right side always read-only
                    ]
                },
                parent: container,
                orientation: this._mergeOptions.orientation,
                highlightChanges: this.highlightChanges,
                gutter: this._mergeOptions.gutter,
                revertControls: this._mergeOptions.revertControls,
                collapseUnchanged: collapseConfig
            });

            // Store references to individual editors
            this._editor = this._mergeView.a;
            this._rightEditor = this._mergeView.b;

            // Setup scroll sync if enabled
            if (this._scrollSyncEnabled) {
                this._setupScrollSync();
            }
        } catch (error) {
            console.error('[JSON MergeView] Error initializing:', error);
        }
    }

    _setupScrollSync() {
        if (!this._editor || !this._rightEditor) return;

        const leftScroller = this._editor.scrollDOM;
        const rightScroller = this._rightEditor.scrollDOM;

        const syncScroll = (source, target) => {
            if (this._scrollSyncing) return;
            this._scrollSyncing = true;

            // Calculate scroll ratio
            const sourceMax = source.scrollHeight - source.clientHeight;
            const targetMax = target.scrollHeight - target.clientHeight;

            if (sourceMax > 0 && targetMax > 0) {
                const ratio = source.scrollTop / sourceMax;
                target.scrollTop = ratio * targetMax;
            }

            requestAnimationFrame(() => {
                this._scrollSyncing = false;
            });
        };

        leftScroller.addEventListener('scroll', () => {
            if (this._scrollSyncEnabled) {
                syncScroll(leftScroller, rightScroller);
            }
        });

        rightScroller.addEventListener('scroll', () => {
            if (this._scrollSyncEnabled) {
                syncScroll(rightScroller, leftScroller);
            }
        });
    }

    _onDocumentChanged(newValue) {
        if (this.document === newValue) return;
        this.document = newValue;

        if (this.$server?.onDocumentChanged) {
            this.$server.onDocumentChanged(newValue);
        }
    }

    // Public API (called from Vaadin)

    setEditorDocument(element, doc) {
        if (this.document === doc) return;

        this._isInternalUpdate = true;
        try {
            this.document = doc;

            if (this._editor) {
                this._editor.dispatch({
                    changes: {
                        from: 0,
                        to: this._editor.state.doc.length,
                        insert: doc || ''
                    }
                });
            }
        } finally {
            this._isInternalUpdate = false;
        }
    }

    setDarkTheme(isDark) {
        this.isDarkTheme = isDark;
        this.setAttribute('data-theme', isDark ? 'dark' : 'light');
        const effect = themeCompartment.reconfigure(isDark ? oneDark : []);

        if (this._editor) {
            this._editor.dispatch({ effects: effect });
        }
        if (this._rightEditor) {
            this._rightEditor.dispatch({ effects: effect });
        }
    }

    setReadOnly(readOnly) {
        this.isReadOnly = readOnly;
        this.setAttribute('data-readonly', readOnly ? 'true' : 'false');
        const effect = readOnlyCompartment.reconfigure(EditorState.readOnly.of(readOnly));

        if (this._editor) {
            this._editor.dispatch({ effects: effect });
        }
    }

    setLint(lint) {
        this.isLint = lint;
        const effect = lintCompartment.reconfigure(lint ? [linter(jsonParseLinter()), lintGutter()] : []);

        if (this._editor) {
            this._editor.dispatch({ effects: effect });
        }
        if (this._rightEditor) {
            this._rightEditor.dispatch({ effects: effect });
        }
    }

    setMatchBrackets(enabled) {
        this.matchBrackets = enabled;
        const effect = bracketMatchingCompartment.reconfigure(enabled ? bracketMatching() : []);

        if (this._editor) {
            this._editor.dispatch({ effects: effect });
        }
        if (this._rightEditor) {
            this._rightEditor.dispatch({ effects: effect });
        }
    }

    setAutoCloseBrackets(enabled) {
        this.autoCloseBrackets = enabled;
        const effect = closeBracketsCompartment.reconfigure(enabled ? closeBrackets() : []);

        if (this._editor) {
            this._editor.dispatch({ effects: effect });
        }
        if (this._rightEditor) {
            this._rightEditor.dispatch({ effects: effect });
        }
    }

    setMergeModeEnabled(enabled) {
        if (this.isMergeMode === enabled) return;

        this.isMergeMode = enabled;

        // Save current document before rebuilding
        const currentDoc = this.getDocument();
        this.document = currentDoc;

        // Clear container and rebuild editor
        const container = this.shadowRoot.getElementById('editor-container');
        if (container) {
            this._destroyEditors();
            this._initEditor();
        }
    }

    setMergeRightContent(content) {
        this.mergeRightContent = content || '';

        // CM6 MergeView doesn't support dynamic content updates well,
        // so we need to rebuild it
        if (this.isMergeMode && this._mergeView) {
            this._rebuildMergeView();
        } else if (this._rightEditor) {
            this._isInternalUpdate = true;
            try {
                this._rightEditor.dispatch({
                    changes: {
                        from: 0,
                        to: this._rightEditor.state.doc.length,
                        insert: content || ''
                    }
                });
            } finally {
                this._isInternalUpdate = false;
            }
        }
    }

    // CM6 MergeView API methods

    setRevertControls(direction) {
        // direction: 'a-to-b' | 'b-to-a' | null
        this._mergeOptions.revertControls = direction;
        if (this.isMergeMode) {
            this._rebuildMergeView();
        }
    }

    setGutter(enabled) {
        this._mergeOptions.gutter = enabled;
        if (this.isMergeMode) {
            this._rebuildMergeView();
        }
    }

    setHighlightChanges(enabled) {
        this.highlightChanges = enabled;
        // Requires rebuild - CM6 MergeView doesn't support dynamic reconfiguration
        if (this.isMergeMode) {
            this._rebuildMergeView();
        }
    }

    setCollapseUnchanged(enabled) {
        this.collapseUnchanged = enabled;
        // Requires rebuild
        if (this.isMergeMode) {
            this._rebuildMergeView();
        }
    }

    setCollapseUnchangedMargin(margin) {
        this._mergeOptions.collapseUnchangedMargin = margin;
        if (this.isMergeMode && this.collapseUnchanged) {
            this._rebuildMergeView();
        }
    }

    setSyncScroll(enabled) {
        this._scrollSyncEnabled = enabled;
        // If enabling and merge view exists, setup listeners
        if (enabled && this.isMergeMode && this._editor && this._rightEditor) {
            this._setupScrollSync();
        }
    }

    // Legacy method for compatibility - maps to new API
    setMergeOption(option, value) {
        switch (option) {
            case 'revertControls':
                this.setRevertControls(value);
                break;
            case 'gutter':
                this.setGutter(value);
                break;
            case 'highlightChanges':
                this.setHighlightChanges(value);
                break;
            case 'collapseUnchanged':
                this.setCollapseUnchanged(value);
                break;
            case 'syncScroll':
            case 'connect':
                this.setSyncScroll(!!value);
                break;
            default:
                console.warn(`[JSON Editor] Unknown merge option: ${option}`);
        }
    }

    _rebuildMergeView() {
        const currentDoc = this.getDocument();
        const currentRight = this.getMergeRightContent();
        this.document = currentDoc;
        this.mergeRightContent = currentRight;

        const container = this.shadowRoot.getElementById('editor-container');
        if (container) {
            this._destroyEditors();
            this._initEditor();
        }
    }

    goToNextChange() {
        if (this._mergeView) {
            // Use MergeView's built-in chunk navigation
            const chunks = this._mergeView.chunks;
            if (!chunks || chunks.length === 0) return;

            this._currentChunkIndex++;
            if (this._currentChunkIndex >= chunks.length) {
                this._currentChunkIndex = 0;
            }

            const chunk = chunks[this._currentChunkIndex];
            if (chunk && this._editor) {
                this._editor.dispatch({
                    selection: { anchor: chunk.fromA },
                    scrollIntoView: true
                });
            }
        }
    }

    goToPreviousChange() {
        if (this._mergeView) {
            const chunks = this._mergeView.chunks;
            if (!chunks || chunks.length === 0) return;

            this._currentChunkIndex--;
            if (this._currentChunkIndex < 0) {
                this._currentChunkIndex = chunks.length - 1;
            }

            const chunk = chunks[this._currentChunkIndex];
            if (chunk && this._editor) {
                this._editor.dispatch({
                    selection: { anchor: chunk.fromA },
                    scrollIntoView: true
                });
            }
        }
    }

    getMergeRightContent() {
        if (this._rightEditor) {
            return this._rightEditor.state.doc.toString();
        }
        return this.mergeRightContent || '';
    }

    getDocument() {
        if (this._editor) {
            return this._editor.state.doc.toString();
        }
        return this.document || '';
    }

    onRefreshEditor() {
        // CM6 doesn't need explicit refresh like CM5
    }

    appendText(text) {
        if (!this._editor) return;

        const length = this._editor.state.doc.length;
        this._editor.dispatch({
            changes: { from: length, insert: text + '\n' }
        });
    }

    scrollToBottom() {
        if (!this._editor) return;
        this._editor.scrollDOM.scrollTop = this._editor.scrollDOM.scrollHeight;
    }
}

customElements.define('json-editor-lsp', JsonEditorLsp);
