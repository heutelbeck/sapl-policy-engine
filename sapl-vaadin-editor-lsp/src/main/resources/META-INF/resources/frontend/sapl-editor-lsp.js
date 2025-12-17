/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * SAPL Editor using CodeMirror 6 with LSP integration and merge view support.
 * Provides syntax highlighting, diagnostics, completion, and diff view via Language Server Protocol.
 */
import { LitElement, html, css } from 'lit';
import { EditorView, basicSetup } from 'codemirror';
import { EditorState, Compartment, StateField, StateEffect } from '@codemirror/state';
import { keymap, Decoration } from '@codemirror/view';
import { indentWithTab } from '@codemirror/commands';
import { oneDark } from '@codemirror/theme-one-dark';
import { StreamLanguage, bracketMatching } from '@codemirror/language';
import { linter, Diagnostic } from '@codemirror/lint';
import { autocompletion, CompletionContext, closeBrackets, completionKeymap, snippet } from '@codemirror/autocomplete';
import { MergeView } from '@codemirror/merge';
import { unifiedMergeView } from '@codemirror/merge';
import { marked } from 'marked';

// Global configuration ID export (for cross-module access like in old editor)
export { saplPdpConfigurationId };
let saplPdpConfigurationId = null;

// Language mode compartments for dynamic switching
const languageCompartment = new Compartment();
const themeCompartment = new Compartment();
const readOnlyCompartment = new Compartment();
const bracketMatchingCompartment = new Compartment();
const closeBracketsCompartment = new Compartment();

// Coverage highlighting - StateEffect and StateField for line decorations
const setCoverageEffect = StateEffect.define();
const clearCoverageEffect = StateEffect.define();

const coverageField = StateField.define({
    create: () => Decoration.none,
    update: (decorations, transaction) => {
        for (const effect of transaction.effects) {
            if (effect.is(setCoverageEffect)) {
                return effect.value;
            }
            if (effect.is(clearCoverageEffect)) {
                return Decoration.none;
            }
        }
        // Map decorations through document changes
        return decorations.map(transaction.changes);
    },
    provide: field => EditorView.decorations.from(field)
});

// Simple SAPL syntax highlighting (fallback when LSP not ready)
const saplLanguage = StreamLanguage.define({
    token(stream) {
        if (stream.match(/\/\/.*/)) return 'comment';
        if (stream.match(/\/\*[\s\S]*?\*\//)) return 'comment';
        if (stream.match(/"(?:[^"\\]|\\.)*"/)) return 'string';
        if (stream.match(/-?\d+(\.\d+)?([eE][+-]?\d+)?/)) return 'number';
        if (stream.match(/\b(policy|set|permit|deny|import|as|where|var|advice|obligation|transform|on)\b/)) return 'keyword';
        if (stream.match(/\b(subject|action|resource|environment)\b/)) return 'variableName.special';
        if (stream.match(/\b(true|false|null|undefined)\b/)) return 'atom';
        if (stream.match(/\b(first-applicable|deny-overrides|permit-overrides|only-one-applicable|deny-unless-permit|permit-unless-deny)\b/)) return 'keyword';
        if (stream.match(/[a-zA-Z_][a-zA-Z0-9_]*/)) return 'variableName';
        if (stream.match(/[+\-*/%<>=!&|^~?:]+/)) return 'operator';
        stream.next();
        return null;
    }
});

// Simple SAPLTest syntax highlighting
const saplTestLanguage = StreamLanguage.define({
    token(stream) {
        if (stream.match(/\/\/.*/)) return 'comment';
        if (stream.match(/\/\*[\s\S]*?\*\//)) return 'comment';
        if (stream.match(/"(?:[^"\\]|\\.)*"/)) return 'string';
        if (stream.match(/-?\d+(\.\d+)?([eE][+-]?\d+)?/)) return 'number';
        if (stream.match(/\b(requirement|scenario|given|when|then|expect)\b/)) return 'keyword';
        if (stream.match(/\b(permit|deny|indeterminate|not-applicable)\b/)) return 'keyword';
        if (stream.match(/\b(subject|action|resource|environment|attempts|on)\b/)) return 'variableName.special';
        if (stream.match(/\b(function|attribute|maps|to|emits|stream|timing|of|is|called|virtual-time|error)\b/)) return 'keyword';
        if (stream.match(/\b(pip|static-pip|function-library|static-function-library)\b/)) return 'keyword';
        if (stream.match(/\b(pdp|variables|combining-algorithm|configuration|policy|set|policies)\b/)) return 'keyword';
        if (stream.match(/\b(matching|any|equals|containing|key|value|where|with|decision|obligation|advice|obligations)\b/)) return 'keyword';
        if (stream.match(/\b(null|text|number|boolean|array|object)\b/)) return 'typeName';
        if (stream.match(/\b(true|false|undefined)\b/)) return 'atom';
        if (stream.match(/[a-zA-Z_][a-zA-Z0-9_]*/)) return 'variableName';
        stream.next();
        return null;
    }
});

class SaplEditorLsp extends LitElement {
    static properties = {
        document: { type: String },
        language: { type: String },
        wsUrl: { type: String },
        isDarkTheme: { type: Boolean },
        isReadOnly: { type: Boolean },
        hasLineNumbers: { type: Boolean },
        isMergeMode: { type: Boolean },
        mergeRightContent: { type: String },
        matchBrackets: { type: Boolean },
        autoCloseBrackets: { type: Boolean },
        highlightChanges: { type: Boolean },
        collapseUnchanged: { type: Boolean },
        configurationId: { type: String },
        autocompleteTrigger: { type: String },  // 'manual' or 'on_typing'
        autocompleteDelay: { type: Number }
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

        /* Diagnostic styles */
        .cm-lintRange-error {
            background: rgba(255, 0, 0, 0.2);
            border-bottom: 2px wavy red;
        }
        .cm-lintRange-warning {
            background: rgba(255, 165, 0, 0.2);
            border-bottom: 2px wavy orange;
        }
        .cm-lintRange-info {
            background: rgba(0, 0, 255, 0.1);
            border-bottom: 2px dotted blue;
        }

        /* Coverage styles */
        :host {
            --sapl-cov-covered:  rgba(46, 204, 113, 0.18);
            --sapl-cov-partial:  rgba(241, 196, 15, 0.20);
            --sapl-cov-uncovered:rgba(231, 76, 60,  0.18);
            --sapl-cov-ignored:  rgba(127, 140, 141,0.14);
        }
        .coverage-covered { background: var(--sapl-cov-covered) !important; }
        .coverage-partial { background: var(--sapl-cov-partial) !important; }
        .coverage-uncovered { background: var(--sapl-cov-uncovered) !important; }
        .coverage-ignored { background: var(--sapl-cov-ignored) !important; }

        /* Completion documentation styles */
        .cm-completion-doc {
            padding: 8px;
            font-size: 13px;
            line-height: 1.4;
            max-width: 500px;
        }
        .cm-completion-doc code {
            background: rgba(0, 0, 0, 0.1);
            padding: 1px 4px;
            border-radius: 3px;
            font-family: monospace;
            font-size: 12px;
        }
        .cm-completion-doc pre {
            background: rgba(0, 0, 0, 0.08);
            padding: 8px;
            border-radius: 4px;
            overflow-x: auto;
            margin: 8px 0;
        }
        .cm-completion-doc pre code {
            background: transparent;
            padding: 0;
        }
        .cm-completion-doc strong {
            font-weight: 600;
        }
        .cm-completion-doc ul {
            margin: 4px 0;
            padding-left: 20px;
        }
        .cm-completion-doc li {
            margin: 2px 0;
        }
        .cm-completion-doc p {
            margin: 4px 0;
        }
        .cm-completion-doc a {
            color: var(--lumo-primary-color, #1976d2);
        }

        /* Completion icons for different types */
        .cm-completionIcon-property::after {
            content: "◈";
            color: #9c27b0;
        }
        .cm-completionIcon-function::after {
            content: "ƒ";
            color: #2196f3;
        }
        .cm-completionIcon-keyword::after {
            content: "⬢";
            color: #ff9800;
        }
        .cm-completionIcon-variable::after {
            content: "𝑥";
            color: #4caf50;
        }
        .cm-completionIcon-module::after {
            content: "◰";
            color: #607d8b;
        }

    `;

    constructor() {
        super();
        this.document = '';
        this.language = 'sapl';
        this.wsUrl = null;
        this.isDarkTheme = false;
        this.isReadOnly = false;
        this.hasLineNumbers = true;
        this.isMergeMode = false;
        this.mergeRightContent = '';
        this.matchBrackets = true;
        this.autoCloseBrackets = true;
        this.highlightChanges = true;
        this.collapseUnchanged = false;
        this.configurationId = null;
        this.autocompleteTrigger = 'manual';  // 'manual' (Ctrl+Space) or 'on_typing'
        this.autocompleteDelay = 300;

        // Internal state
        this._editor = null;
        this._rightEditor = null;
        this._mergeView = null;
        this._ws = null;
        this._pendingRequests = new Map();
        this._requestId = 0;
        this._diagnostics = [];
        this._documentVersion = 0;
        this._lastSentVersion = 0;  // Track version sent to server
        this._lastDiagnosticsVersion = -1;  // Track version for diagnostics validation
        this._isInternalUpdate = false;
        this._completionRequestId = 0;  // Track latest completion request for cancellation

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

        // Coverage data (auto-clear on edit)
        this._lastCoveragePayload = null;

        // Navigation state for diff chunks
        this._currentChunkIndex = -1;
    }

    get _documentUri() {
        const basePath = this.language === 'sapltest'
            ? 'file:///test.sapltest'
            : 'file:///policy.sapl';
        const configId = this.configurationId || 'default';
        return `${basePath}?configurationId=${encodeURIComponent(configId)}`;
    }

    render() {
        return html`<div id="editor-container"></div>`;
    }

    firstUpdated() {
        // Set initial attributes for CSS selectors
        this.setAttribute('data-theme', this.isDarkTheme ? 'dark' : 'light');
        this.setAttribute('data-readonly', this.isReadOnly ? 'true' : 'false');
        this._initEditor();
        if (this.wsUrl) {
            this._connectLsp();
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this._disconnectLsp();
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

    _getLanguageMode() {
        return this.language === 'sapltest' ? saplTestLanguage : saplLanguage;
    }

    _getBaseExtensions(withLinting = true) {
        const extensions = [
            basicSetup,
            keymap.of([indentWithTab]),
            languageCompartment.of(this._getLanguageMode()),
            themeCompartment.of(this.isDarkTheme ? oneDark : []),
            readOnlyCompartment.of(EditorState.readOnly.of(this.isReadOnly)),
            bracketMatchingCompartment.of(this.matchBrackets ? bracketMatching() : []),
            closeBracketsCompartment.of(this.autoCloseBrackets ? closeBrackets() : [])
        ];

        if (withLinting) {
            extensions.push(linter(() => this._getLspDiagnostics()));
            // Configure autocompletion - always active to support LSP trigger characters
            // LSP trigger characters: '.', '<', '|' are handled in _getCompletions
            extensions.push(autocompletion({
                override: [context => this._getCompletions(context)],
                activateOnTyping: true,  // Always on to catch trigger characters
                activateOnTypingDelay: this.autocompleteTrigger === 'on_typing' ? this.autocompleteDelay : 50
            }));
            // Add completion keymap for Ctrl+Space manual trigger
            extensions.push(keymap.of(completionKeymap));
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
            coverageField,
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
                languageCompartment.of(this._getLanguageMode()),
                themeCompartment.of(this.isDarkTheme ? oneDark : []),
                bracketMatchingCompartment.of(this.matchBrackets ? bracketMatching() : []),
                closeBracketsCompartment.of(this.autoCloseBrackets ? closeBrackets() : []),
                cursorTheme
            ];

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
                        readOnlyCompartment.of(EditorState.readOnly.of(this.isReadOnly)),
                        EditorView.updateListener.of(update => {
                            if (update.docChanged && !this._isInternalUpdate) {
                                this.document = update.state.doc.toString();
                                this._documentVersion++;
                                this._sendDidChange(this.document);
                                if (this.$server?.onDocumentChanged) {
                                    this.$server.onDocumentChanged(this.document);
                                }
                            }
                        }),
                        linter(() => this._getLspDiagnostics()),
                        // Configure autocompletion - always active to support LSP trigger characters
                        autocompletion({
                            override: [context => this._getCompletions(context)],
                            activateOnTyping: true,
                            activateOnTypingDelay: this.autocompleteTrigger === 'on_typing' ? this.autocompleteDelay : 50
                        }),
                        keymap.of(completionKeymap)
                    ]
                },
                b: {
                    doc: this.mergeRightContent || '',
                    extensions: [
                        ...baseExtensions,
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
            console.error('[SAPL MergeView] Error initializing:', error);
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
        this._documentVersion++;

        // Auto-clear coverage on edit (coverage data becomes stale)
        this._clearCoverageInternal();

        // Notify LSP server of document change
        this._sendDidChange(newValue);

        // Notify Vaadin server
        if (this.$server?.onDocumentChanged) {
            this.$server.onDocumentChanged(newValue);
        }
    }

    // --- LSP WebSocket Connection ---

    _connectLsp() {
        if (!this.wsUrl || this._ws) return;

        try {
            this._ws = new WebSocket(this.wsUrl);

            this._ws.onopen = () => {
                console.log('[SAPL LSP] Connected');
                this._sendInitialize();
            };

            this._ws.onmessage = (event) => {
                try {
                    const dataSize = event.data?.length || 0;
                    console.log('[SAPL LSP] Received message, size:', dataSize);
                    if (dataSize > 10000) {
                        console.log('[SAPL LSP] Large message preview:', event.data.substring(0, 200) + '...');
                    }
                    const message = JSON.parse(event.data);
                    console.log('[SAPL LSP] Parsed message, id:', message.id, 'method:', message.method,
                                'hasResult:', 'result' in message, 'hasError:', 'error' in message);
                    this._handleLspMessage(message);
                } catch (e) {
                    console.error('[SAPL LSP] Failed to parse message:', e, 'data preview:', event.data?.substring(0, 500));
                }
            };

            this._ws.onerror = (error) => {
                console.error('[SAPL LSP] WebSocket error:', error);
            };

            this._ws.onclose = () => {
                console.log('[SAPL LSP] Disconnected');
                this._ws = null;
                // Reject all pending requests
                this._pendingRequests.forEach((pending, id) => {
                    pending.reject(new Error('WebSocket closed'));
                });
                this._pendingRequests.clear();
                // Attempt reconnect after delay
                setTimeout(() => this._connectLsp(), 5000);
            };
        } catch (e) {
            console.error('[SAPL LSP] Connection failed:', e);
        }
    }

    _disconnectLsp() {
        if (this._ws) {
            // Reject all pending requests before closing
            this._pendingRequests.forEach((pending, id) => {
                pending.reject(new Error('LSP disconnected'));
            });
            this._pendingRequests.clear();
            this._ws.close();
            this._ws = null;
        }
    }

    _sendLspRequest(method, params, timeoutMs = 10000) {
        return new Promise((resolve, reject) => {
            if (!this._ws || this._ws.readyState !== WebSocket.OPEN) {
                reject(new Error('LSP not connected'));
                return;
            }

            const id = ++this._requestId;
            const message = {
                jsonrpc: '2.0',
                id,
                method,
                params
            };

            // Set up timeout to prevent hanging requests
            const timeoutId = setTimeout(() => {
                if (this._pendingRequests.has(id)) {
                    this._pendingRequests.delete(id);
                    console.warn(`[SAPL LSP] Request ${method} timed out after ${timeoutMs}ms`);
                    reject(new Error(`Request timed out: ${method}`));
                }
            }, timeoutMs);

            this._pendingRequests.set(id, {
                resolve: (result) => {
                    clearTimeout(timeoutId);
                    console.log('[SAPL LSP] Request', id, method, 'resolved');
                    resolve(result);
                },
                reject: (error) => {
                    clearTimeout(timeoutId);
                    reject(error);
                }
            });
            console.log('[SAPL LSP] Sending request', id, method);
            this._ws.send(JSON.stringify(message));
        });
    }

    _sendLspNotification(method, params) {
        if (!this._ws || this._ws.readyState !== WebSocket.OPEN) return;

        const message = {
            jsonrpc: '2.0',
            method,
            params
        };
        this._ws.send(JSON.stringify(message));
    }

    _handleLspMessage(message) {
        if ('id' in message && message.id !== null) {
            const pending = this._pendingRequests.get(message.id);
            if (pending) {
                this._pendingRequests.delete(message.id);
                if ('error' in message) {
                    pending.reject(message.error);
                } else {
                    pending.resolve(message.result);
                }
            }
            return;
        }

        if ('method' in message) {
            this._handleLspNotification(message.method, message.params);
        }
    }

    _handleLspNotification(method, params) {
        switch (method) {
            case 'textDocument/publishDiagnostics':
                this._handleDiagnostics(params);
                break;
            case 'window/logMessage':
                console.log('[LSP]', params.message);
                break;
        }
    }

    async _sendInitialize() {
        try {
            const result = await this._sendLspRequest('initialize', {
                processId: null,
                capabilities: {
                    textDocument: {
                        synchronization: { dynamicRegistration: false, didSave: true },
                        completion: { completionItem: { snippetSupport: false } },
                        publishDiagnostics: { relatedInformation: true }
                    }
                },
                rootUri: null
            });

            this._sendLspNotification('initialized', {});
            this._sendDidOpen();

            console.log('[SAPL LSP] Initialized:', result.capabilities);
        } catch (e) {
            console.error('[SAPL LSP] Initialize failed:', e);
        }
    }

    _sendDidOpen() {
        this._sendLspNotification('textDocument/didOpen', {
            textDocument: {
                uri: this._documentUri,
                languageId: this.language,
                version: this._documentVersion,
                text: this.document || ''
            }
        });
    }

    _sendDidChange(text) {
        // Track the version we're sending for diagnostics validation
        this._lastSentVersion = this._documentVersion;
        this._sendLspNotification('textDocument/didChange', {
            textDocument: {
                uri: this._documentUri,
                version: this._documentVersion
            },
            contentChanges: [{ text }]
        });
    }

    _handleDiagnostics(params) {
        if (params.uri !== this._documentUri) return;

        // Ignore diagnostics if document has changed since we sent the last update
        // This prevents stale diagnostics from showing incorrect errors
        if (this._lastSentVersion !== undefined && this._lastSentVersion !== this._documentVersion) {
            console.debug('[SAPL LSP] Ignoring stale diagnostics (version mismatch)');
            return;
        }

        this._diagnostics = (params.diagnostics || []).map(d => ({
            from: this._positionToOffset(d.range.start),
            to: this._positionToOffset(d.range.end),
            severity: this._mapSeverity(d.severity),
            message: d.message,
            source: d.source || 'sapl'
        }));

        // Remember which version these diagnostics are for
        this._lastDiagnosticsVersion = this._documentVersion;

        // Trigger lint update
        if (this._editor) {
            this._editor.dispatch({});
        }

        // Notify Vaadin server
        if (this.$server?.onValidation) {
            const issues = this._diagnostics.map(d => ({
                description: d.message,
                severity: d.severity === 'error' ? 'ERROR' : d.severity === 'warning' ? 'WARNING' : 'INFO',
                startLine: this._offsetToLine(d.from),
                startColumn: this._offsetToColumn(d.from),
                endLine: this._offsetToLine(d.to),
                endColumn: this._offsetToColumn(d.to)
            }));
            this.$server.onValidation(issues);
        }
    }

    _getLspDiagnostics() {
        // Don't show diagnostics if they're for an older document version
        if (this._lastDiagnosticsVersion !== this._documentVersion) {
            return [];
        }
        return this._diagnostics.map(d => ({
            from: d.from,
            to: d.to,
            severity: d.severity,
            message: d.message,
            source: d.source
        }));
    }

    async _getCompletions(context) {
        if (!this._ws || this._ws.readyState !== WebSocket.OPEN) {
            return null;
        }

        // Check for LSP trigger characters and word patterns
        // Trigger on: '.', '<', '|<', or word characters (for prefix matching)
        const attrMatch = context.matchBefore(/\|?<[\w.]*$/);
        const dotMatch = context.matchBefore(/\.[\w]*$/);
        const wordMatch = context.matchBefore(/[\w]+$/);

        // DEBUG: Log trigger detection
        if (attrMatch) console.log('[SAPL] Attr trigger:', attrMatch.text);

        // Only request completions if:
        // 1. Explicit request (Ctrl+Space)
        // 2. Attribute trigger (< or |<)
        // 3. Dot trigger (.)
        // 4. At least 1 character of word prefix
        const isTrigger = attrMatch || dotMatch;
        const hasWordPrefix = wordMatch && wordMatch.text.length >= 1;

        if (!context.explicit && !isTrigger && !hasWordPrefix) {
            return null;  // Skip completion for non-meaningful contexts
        }

        // Track this completion request to detect if it becomes stale
        const requestVersion = this._documentVersion;
        const thisRequestId = ++this._completionRequestId;

        try {
            const pos = this._offsetToPosition(context.pos);
            const result = await this._sendLspRequest('textDocument/completion', {
                textDocument: { uri: this._documentUri },
                position: pos
            }, 5000);  // 5 second timeout for completions

            // Check if document changed while we were waiting - discard stale results
            if (this._documentVersion !== requestVersion || this._completionRequestId !== thisRequestId) {
                return null;
            }

            if (!result) return null;

            // Determine 'from' position based on context
            let from;
            if (attrMatch) {
                // Include the < or |< in replacement range
                from = attrMatch.from;
            } else if (dotMatch) {
                // Start after the dot
                from = dotMatch.from + 1;
            } else {
                // Use word match or cursor position
                const generalWordMatch = context.matchBefore(/[\w.]*$/);
                from = generalWordMatch ? generalWordMatch.from : context.pos;
            }

            const items = Array.isArray(result) ? result : (result.items || []);

            console.log('[SAPL] Completion result:', { from, itemCount: items.length, pos: context.pos });

            return {
                from: from,
                options: items.map(item => {
                    let insertText = item.insertText || item.label || '';

                    // Check if this is an LSP snippet (has insertTextFormat = 2 or contains ${)
                    const isSnippet = item.insertTextFormat === 2 || /\$\{\d+:/.test(insertText);

                    if (isSnippet) {
                        // Convert LSP snippet syntax to CodeMirror snippet syntax
                        const cmSnippet = this._convertLspSnippet(insertText);
                        return {
                            label: item.label || '',
                            apply: snippet(cmSnippet),
                            detail: item.detail,
                            info: this._renderDocumentation(item.documentation),
                            type: this._mapCompletionKind(item.kind)
                        };
                    } else {
                        return {
                            label: item.label || '',
                            apply: insertText,
                            detail: item.detail,
                            info: this._renderDocumentation(item.documentation),
                            type: this._mapCompletionKind(item.kind)
                        };
                    }
                })
            };
        } catch (e) {
            console.error('[SAPL LSP] Completion failed:', e);
            return null;
        }
    }

    /**
     * Converts LSP snippet syntax to CodeMirror snippet syntax.
     * LSP: ${1:paramName}  -> CodeMirror: ${paramName}
     * LSP: $1              -> CodeMirror: ${}
     */
    _convertLspSnippet(lspSnippet) {
        // Convert ${n:name} to ${name} (CodeMirror uses named placeholders)
        // Also handle $n without braces
        return lspSnippet
            .replace(/\$\{(\d+):([^}]+)\}/g, '${$2}')  // ${1:name} -> ${name}
            .replace(/\$(\d+)/g, '${}');               // $1 -> ${}
    }

    /**
     * Renders LSP documentation (markdown or plain text) for display.
     * Returns a function that creates a DOM element for markdown content.
     * Uses the 'marked' library for proper markdown parsing.
     */
    _renderDocumentation(documentation) {
        if (!documentation) return undefined;

        const content = documentation.value || documentation;
        if (!content || typeof content !== 'string') return undefined;

        // Detect markdown by kind or common patterns (bold, code fences, headers, lists)
        const isMarkdown = documentation.kind === 'markdown' ||
            content.includes('**') ||
            content.includes('```') ||
            content.includes('# ') ||
            content.includes('- ');

        if (isMarkdown) {
            return () => {
                const div = document.createElement('div');
                div.className = 'cm-completion-doc';
                try {
                    if (typeof marked !== 'undefined' && marked.parse) {
                        div.innerHTML = marked.parse(content, { breaks: true, gfm: true });
                    } else {
                        // Fallback: basic markdown conversion if marked library not available
                        div.innerHTML = this._basicMarkdownToHtml(content);
                    }
                } catch (e) {
                    console.error('[SAPL] Markdown parsing failed:', e);
                    div.innerHTML = this._basicMarkdownToHtml(content);
                }
                return div;
            };
        }

        return content;
    }

    /**
     * Basic markdown to HTML conversion as fallback when marked library unavailable.
     * Handles: code fences, inline code, bold, headers, lists, links, paragraphs.
     */
    _basicMarkdownToHtml(markdown) {
        let html = markdown
            // Escape HTML first
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            // Code fences with language specifier
            .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>')
            // Code fences without language
            .replace(/```\n?([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
            // Inline code
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            // Bold
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            // Italic
            .replace(/\*([^*]+)\*/g, '<em>$1</em>')
            // Headers
            .replace(/^### (.+)$/gm, '<h4>$1</h4>')
            .replace(/^## (.+)$/gm, '<h3>$1</h3>')
            .replace(/^# (.+)$/gm, '<h2>$1</h2>')
            // Links
            .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
            // List items
            .replace(/^- (.+)$/gm, '<li>$1</li>')
            // Paragraphs (double newlines)
            .replace(/\n\n/g, '</p><p>')
            // Single newlines to <br>
            .replace(/\n/g, '<br>');

        // Wrap in paragraph if not already structured
        if (!html.startsWith('<')) {
            html = '<p>' + html + '</p>';
        }

        // Clean up empty paragraphs
        html = html.replace(/<p><\/p>/g, '').replace(/<p><br>/g, '<p>');

        return html;
    }

    // --- Utility Methods ---

    _positionToOffset(pos) {
        if (!this._editor) return 0;
        const doc = this._editor.state.doc;
        const line = doc.line(Math.min(pos.line + 1, doc.lines));
        return Math.min(line.from + pos.character, line.to);
    }

    _offsetToPosition(offset) {
        if (!this._editor) return { line: 0, character: 0 };
        const doc = this._editor.state.doc;
        const line = doc.lineAt(offset);
        return {
            line: line.number - 1,
            character: offset - line.from
        };
    }

    _offsetToLine(offset) {
        if (!this._editor) return 1;
        return this._editor.state.doc.lineAt(offset).number;
    }

    _offsetToColumn(offset) {
        if (!this._editor) return 0;
        const line = this._editor.state.doc.lineAt(offset);
        return offset - line.from;
    }

    _mapSeverity(severity) {
        switch (severity) {
            case 1: return 'error';
            case 2: return 'warning';
            case 3: return 'info';
            case 4: return 'hint';
            default: return 'info';
        }
    }

    _mapCompletionKind(kind) {
        const kinds = {
            1: 'text', 2: 'method', 3: 'function', 4: 'constructor',
            5: 'field', 6: 'variable', 7: 'class', 8: 'interface',
            9: 'module', 10: 'property', 11: 'unit', 12: 'value',
            13: 'enum', 14: 'keyword', 15: 'snippet', 16: 'color',
            17: 'file', 18: 'reference', 19: 'folder', 20: 'enumMember',
            21: 'constant', 22: 'struct', 23: 'event', 24: 'operator',
            25: 'typeParameter'
        };
        return kinds[kind] || 'text';
    }

    // --- Coverage API ---

    _clearCoverageInternal() {
        this._lastCoveragePayload = null;
        if (this._editor) {
            this._editor.dispatch({
                effects: clearCoverageEffect.of(null)
            });
        }
    }

    clearCoverage() {
        this._clearCoverageInternal();
    }

    /**
     * Sets coverage highlighting for the document.
     * @param {Array} data - Array of {line, status, summary} objects
     *   - line: 1-based line number
     *   - status: 'covered' | 'partial' | 'uncovered' | 'ignored'
     *   - summary: optional tooltip text (e.g., "2 of 4 branches covered")
     */
    setCoverageData(data) {
        this._lastCoveragePayload = data;
        if (!this._editor || !data) return;

        const doc = this._editor.state.doc;
        const decorations = [];

        for (const item of data) {
            const lineNum = item.line;
            if (lineNum < 1 || lineNum > doc.lines) continue;

            // Skip 'ignored' status lines (no conditions to cover)
            if (item.status === 'ignored') continue;

            const line = doc.line(lineNum);
            const cssClass = `coverage-${item.status}`;

            // Create line decoration with optional tooltip
            const spec = { class: cssClass };
            if (item.summary) {
                spec.attributes = { title: item.summary };
            }

            decorations.push(
                Decoration.line(spec).range(line.from)
            );
        }

        // Sort by position (required by CodeMirror)
        decorations.sort((a, b) => a.from - b.from);

        this._editor.dispatch({
            effects: setCoverageEffect.of(Decoration.set(decorations))
        });
    }

    // --- Public API (called from Vaadin) ---

    setEditorDocument(element, doc) {
        if (this.document === doc) return;

        this._isInternalUpdate = true;
        try {
            this.document = doc;
            this._documentVersion++;

            if (this._editor) {
                this._editor.dispatch({
                    changes: {
                        from: 0,
                        to: this._editor.state.doc.length,
                        insert: doc || ''
                    }
                });
            }

            this._sendDidChange(doc);
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

    setConfigurationId(configId) {
        this.configurationId = configId;
        // Update global export for cross-module access
        saplPdpConfigurationId = configId;
    }

    getConfigurationId() {
        return this.configurationId;
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

    setAutocompleteTrigger(trigger) {
        this.autocompleteTrigger = trigger;
        // Rebuild editor to apply autocomplete configuration change
        this._rebuildEditor();
    }

    setAutocompleteDelay(delayMs) {
        this.autocompleteDelay = delayMs;
        // Only rebuild if using on_typing mode
        if (this.autocompleteTrigger === 'on_typing') {
            this._rebuildEditor();
        }
    }

    _rebuildEditor() {
        // Save current state
        const currentDoc = this.getDocument();
        const container = this.shadowRoot.getElementById('editor-container');
        if (!container) return;

        // Destroy existing editors
        this._destroyEditors();

        // Reinitialize with new settings
        if (this.isMergeMode) {
            this._initMergeView(container);
        } else {
            this._initSingleEditor(container);
        }

        // Restore document content
        if (currentDoc) {
            this.setEditorDocument(this, currentDoc);
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
                console.warn(`[SAPL Editor] Unknown merge option: ${option}`);
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

    scrollToBottom() {
        if (this._editor) {
            const doc = this._editor.state.doc;
            this._editor.dispatch({
                selection: { anchor: doc.length },
                scrollIntoView: true
            });
        }
    }
}

customElements.define('sapl-editor-lsp', SaplEditorLsp);
