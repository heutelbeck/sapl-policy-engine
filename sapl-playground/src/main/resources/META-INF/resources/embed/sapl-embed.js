/*
 * SAPL Playground Embed Script
 *
 * Renders a read-only CodeMirror placeholder with SAPL syntax highlighting.
 * On click, lazy-loads the interactive Vaadin web component.
 *
 * Usage:
 *   <sapl-demo>
 *       <script type="sapl/policy">
 *   policy "example"
 *   permit
 *     action == "read"
 *       </script>
 *       <script type="sapl/subscription">
 *   {"subject": "alice", "action": "read", "resource": "document"}
 *       </script>
 *   </sapl-demo>
 *   <script type="module" src="http://playground-host/embed/sapl-embed.js"></script>
 */

var PLAYGROUND_ORIGIN = new URL(import.meta.url).origin;
var WC_SCRIPT_URL = PLAYGROUND_ORIGIN + '/web-component/sapl-playground.js';
var wcScriptLoaded = false;

function injectStyles() {
    if (document.getElementById('sapl-embed-styles')) return;
    var style = document.createElement('style');
    style.id = 'sapl-embed-styles';
    style.textContent =
        'sapl-demo { display: block; }' +
        'sapl-demo .sapl-placeholder {' +
        '  cursor: pointer;' +
        '  border: 1px solid rgba(255, 255, 255, 0.1);' +
        '  border-radius: 8px;' +
        '  overflow: hidden;' +
        '}' +
        'sapl-demo .sapl-placeholder .cm-editor { cursor: pointer; }' +
        'sapl-demo .sapl-hint {' +
        '  padding: 0.5em 1em;' +
        '  color: #6c7086;' +
        '  font-size: 0.85em;' +
        '  text-align: center;' +
        '  cursor: pointer;' +
        '  transition: color 0.2s;' +
        '  background: hsl(210, 10%, 12%);' +
        '}' +
        'sapl-demo .sapl-hint:hover { color: #cdd6f4; }' +
        'sapl-demo sapl-playground { display: block; }' +
        'sapl-demo sapl-playground:not(:defined) { display: none; }';
    document.head.appendChild(style);
}

var saplLanguage;

async function loadCodeMirror() {
    var [{basicSetup, EditorView}, {StreamLanguage}, {oneDark}] = await Promise.all([
        import('https://esm.sh/codemirror'),
        import('https://esm.sh/@codemirror/language'),
        import('https://esm.sh/@codemirror/theme-one-dark')
    ]);

    saplLanguage = StreamLanguage.define({
        token: function(stream) {
            if (stream.match(/\/\/.*/)) return 'comment';
            if (stream.match(/\/\*[\s\S]*?\*\//)) return 'comment';
            if (stream.match(/"(?:[^"\\]|\\.)*"/)) return 'string';
            if (stream.match(/-?\d+(\.\d+)?([eE][+-]?\d+)?/)) return 'number';
            if (stream.match(/\b(policy|set|permit|deny|import|as|var|advice|obligation|transform|on)\b/)) return 'keyword';
            if (stream.match(/\b(subject|action|resource|environment)\b/)) return 'variableName.special';
            if (stream.match(/\b(true|false|null|undefined)\b/)) return 'atom';
            if (stream.match(/\b(first|priority|unanimous|strict|unique|or|abstain|errors|propagate)\b/)) return 'keyword';
            if (stream.match(/[a-zA-Z_][a-zA-Z0-9_]*/)) return 'variableName';
            if (stream.match(/[+\-*/%<>=!&|^~?:]+/)) return 'operator';
            stream.next();
            return null;
        }
    });

    return { basicSetup, EditorView, oneDark };
}

function loadWcScript() {
    if (wcScriptLoaded) return;
    wcScriptLoaded = true;
    var script = document.createElement('script');
    script.type = 'module';
    script.src = WC_SCRIPT_URL;
    document.head.appendChild(script);
}

async function initDemo(container) {
    var policyEl = container.querySelector('script[type="sapl/policy"]');
    var subscriptionEl = container.querySelector('script[type="sapl/subscription"]');

    var policy = policyEl ? policyEl.textContent.trim() : '';
    var subscription = subscriptionEl ? subscriptionEl.textContent.trim() : '{}';

    var { basicSetup, EditorView, oneDark } = await loadCodeMirror();

    container.innerHTML = '';

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

    new EditorView({
        doc: policy,
        extensions: [basicSetup, saplLanguage, oneDark, EditorView.editable.of(false)],
        parent: editorDiv
    });

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
document.querySelectorAll('sapl-demo').forEach(initDemo);
