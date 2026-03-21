/*
 * SAPL Embed Script
 *
 * Provides CodeMirror-based SAPL syntax highlighting for all SAPL code blocks
 * and adds interactive playground loading for demo blocks.
 *
 * Element types:
 *
 *   <sapl-code>   -- static read-only CodeMirror editor (replaces Rouge highlighting)
 *   <sapl-demo>   -- interactive: CodeMirror + "Try it live" click-to-load playground
 *
 * Both elements expect a <pre> fallback and (for demos) hidden <script> blocks:
 *
 *   <sapl-demo>
 *       <pre class="sapl-fallback"><code>policy "example" permit</code></pre>
 *       <script type="sapl/policy">policy "example" permit</script>
 *       <script type="sapl/subscription">{"subject":"alice",...}</script>
 *   </sapl-demo>
 *
 *   <sapl-code>
 *       <pre class="sapl-fallback"><code>policy "example" permit</code></pre>
 *   </sapl-code>
 *
 * Load via: <script type="module" src="https://playground.sapl.io/embed/sapl-embed.js"></script>
 */

var PLAYGROUND_ORIGIN = new URL(import.meta.url).origin;
var WC_SCRIPT_URL = PLAYGROUND_ORIGIN + '/web-component/sapl-playground.js';
var CM_BUNDLE_URL = PLAYGROUND_ORIGIN + '/embed/codemirror-bundle.min.js';
var wcScriptLoaded = false;

function isDarkMode() {
    var t = document.documentElement.getAttribute('data-theme');
    if (t) return t === 'dark';
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function injectStyles() {
    if (document.getElementById('sapl-embed-styles')) return;
    var style = document.createElement('style');
    style.id = 'sapl-embed-styles';
    style.textContent =
        'sapl-code, sapl-demo { display: block; }' +
        'sapl-code .sapl-fallback, sapl-demo .sapl-fallback { display: none; }' +
        'sapl-code .cm-editor, sapl-demo .cm-editor {' +
        '  border-radius: 8px;' +
        '  overflow: hidden;' +
        '}' +
        'sapl-demo .sapl-placeholder {' +
        '  cursor: pointer;' +
        '  border: 1px solid var(--color-border, rgba(0,0,0,0.1));' +
        '  border-radius: 8px;' +
        '  overflow: hidden;' +
        '}' +
        'sapl-demo .sapl-placeholder .cm-editor {' +
        '  cursor: pointer;' +
        '  border-radius: 0;' +
        '}' +
        'sapl-demo .sapl-hint {' +
        '  padding: 0.5em 1em;' +
        '  color: var(--color-text-secondary, #4a5568);' +
        '  font-size: 0.85em;' +
        '  text-align: center;' +
        '  cursor: pointer;' +
        '  transition: color 0.2s, background-color 0.3s;' +
        '  background: var(--color-code-label-bg, #e2e8f0);' +
        '}' +
        'sapl-demo .sapl-hint:hover { color: var(--color-text, #1a1a2e); }' +
        'sapl-demo sapl-playground { display: block; }' +
        'sapl-demo sapl-playground:not(:defined) { display: none; }';
    document.head.appendChild(style);
}

var cmModules;

async function loadCodeMirror() {
    if (cmModules) return cmModules;

    var cm = await import(CM_BUNDLE_URL);

    var saplLanguage = cm.StreamLanguage.define({
        token: function(stream) {
            if (stream.match(/\/\/.*/)) return 'comment';
            if (stream.match(/\/\*[\s\S]*?\*\//)) return 'comment';
            if (stream.match(/"(?:[^"\\]|\\.)*"/)) return 'string';
            if (stream.match(/-?\d+(\.\d+)?([eE][+-]?\d+)?/)) return 'number';
            if (stream.match(/\b(policy|set|permit|deny|import|as|var|schema|enforced|advice|obligation|transform|in|each|for)\b/)) return 'keyword';
            if (stream.match(/\b(subject|action|resource|environment)\b/)) return 'variableName.special';
            if (stream.match(/\b(true|false|null|undefined)\b/)) return 'atom';
            if (stream.match(/\b(first|priority|unanimous|strict|unique|or|abstain|errors|propagate)\b/)) return 'keyword';
            if (stream.match(/[a-zA-Z_][a-zA-Z0-9_]*/)) return 'variableName';
            if (stream.match(/[+\-*/%<>=!&|^~?:]+/)) return 'operator';
            stream.next();
            return null;
        }
    });

    cmModules = {
        EditorView: cm.EditorView,
        minimalSetup: cm.minimalSetup,
        oneDark: cm.oneDark,
        Compartment: cm.Compartment,
        saplLanguage: saplLanguage
    };
    return cmModules;
}

function loadWcScript() {
    if (wcScriptLoaded) return;
    wcScriptLoaded = true;
    var script = document.createElement('script');
    script.type = 'module';
    script.src = WC_SCRIPT_URL;
    document.head.appendChild(script);
}

function getCode(container) {
    var codeEl = container.querySelector('pre code');
    return codeEl ? codeEl.textContent.trim() : '';
}

var editors = [];

function createEditor(parent, code, modules) {
    var themeComp = new modules.Compartment();
    var dark = isDarkMode();
    var view = new modules.EditorView({
        doc: code,
        extensions: [
            modules.minimalSetup,
            themeComp.of(dark ? modules.oneDark : []),
            modules.saplLanguage,
            modules.EditorView.editable.of(false)
        ],
        parent: parent
    });
    editors.push({ view: view, compartment: themeComp });
    return view;
}

function updateEditorThemes() {
    var dark = isDarkMode();
    editors.forEach(function(e) {
        e.view.dispatch({
            effects: e.compartment.reconfigure(dark ? cmModules.oneDark : [])
        });
    });
}

async function initStatic(container) {
    var code = getCode(container);
    var modules = await loadCodeMirror();

    var editorDiv = document.createElement('div');
    container.appendChild(editorDiv);
    createEditor(editorDiv, code, modules);
}

async function initDemo(container) {
    var policyEl = container.querySelector('script[type="sapl/policy"]');
    var subscriptionEl = container.querySelector('script[type="sapl/subscription"]');

    var policy = policyEl ? policyEl.textContent.trim() : getCode(container);
    var subscription = subscriptionEl ? subscriptionEl.textContent.trim() : '{}';

    var modules = await loadCodeMirror();

    var placeholder = document.createElement('div');
    placeholder.className = 'sapl-placeholder';
    placeholder.setAttribute('role', 'button');
    placeholder.setAttribute('tabindex', '0');
    placeholder.setAttribute('aria-label', 'Click to load interactive SAPL editor');

    var editorDiv = document.createElement('div');
    placeholder.appendChild(editorDiv);

    var hint = document.createElement('div');
    hint.className = 'sapl-hint';
    hint.textContent = 'Try it live';
    placeholder.appendChild(hint);

    container.appendChild(placeholder);
    createEditor(editorDiv, policy, modules);

    var activated = false;

    function activate() {
        if (activated) return;
        activated = true;
        hint.textContent = 'Loading...';
        loadWcScript();

        customElements.whenDefined('sapl-playground').then(function() {
            var editor = document.createElement('sapl-playground');
            editor.setAttribute('policy', policy);
            editor.setAttribute('subscription', subscription);
            container.replaceChild(editor, placeholder);
        });
    }

    placeholder.addEventListener('click', activate);
    placeholder.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            activate();
        }
    });
}

injectStyles();
document.querySelectorAll('sapl-code').forEach(initStatic);
document.querySelectorAll('sapl-demo').forEach(initDemo);

// Watch for theme changes on the host page
new MutationObserver(function() {
    updateEditorThemes();
}).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', updateEditorThemes);
