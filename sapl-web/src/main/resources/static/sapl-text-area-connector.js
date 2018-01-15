/*
 * 
 */
window.io_sapl_grammar_web_SAPLTextArea = function() {
    // Create the component
    var saplEditor = new saplweb.SAPLTextArea(this.getElement());
    // URL basis to load all dependencies from
    var baseUrl = "/";
    // load dependencies
    require.config({
	baseUrl : baseUrl,
	paths : {
	    "jquery" : "webjars/jquery/2.2.3/jquery.min",
	    "xtext/xtext-codemirror" : "xtext/2.13.0/xtext-codemirror"
	},
	packages : [ {
	    name : "codemirror",
	    location : "webjars/codemirror/5.13.2",
	    main : "lib/codemirror"
	} ]
    });
    var editor;

    var _this = this;

    require([ "SaplJSHighlighting", "xtext/xtext-codemirror" ], function(mode,
	    xtext) {
	// now the editor gets created
	editor = xtext.createEditor({
	    baseUrl : baseUrl,
	    // syntaxDefinition : "SaplJSHighlighting",
	    // showErrorDialogs: true,
	    xtextLang : "sapl",
	    sendFullText : true,
	// resourceId: "3a7f22ea.sapl",
	// loadFromServer: false,
	});
	// tweaking options according to https://codemirror.net/doc/manual.html#setOption
	editor.setOption("lineNumbers", true);
	editor.setOption("showCursorWhenSelecting", true);
	
	// initialize the Text View with current server-side state
	editor.doc.setValue(_this.getState().text);
	editor.doc.on("change", function(doc, changeObj) {
	    _this.setText(doc.getValue("\n"));
	});
    });

    // will be used to update the state of client-side component
    this.stateChanged = function() {
	if (editor) {
	    editor.doc.setValue(_this.getState().text);
	}
    }
    
    // Handle changes from the server-side
    this.onStateChange = function() {
	// default state change notification will be triggered on any change of
	// internal state on server side
	
    };

};