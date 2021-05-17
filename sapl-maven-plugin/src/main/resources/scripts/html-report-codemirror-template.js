require.config({
	paths: {
        'jQuery': "https://code.jquery.com/jquery-3.2.1.slim.min",
        'Bootstrap': "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min",
        'Popper': "https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.9/umd/popper.min"
    },    
    shim: {
        Bootstrap: {
            deps: ["jQuery","Popper"],
            exports: "Bootstrap"
        }
    },
	packages: [{
	  name: "codemirror",
	  location: "../assets/codemirror",
	  main: "lib/codemirror"
	}, 
	{
	  name: "sapl-mode",
	  location: "../assets",
	  main: "sapl-mode"
	}, 
	{
	  name: "bootstrap",
	  location: "../assets",
	  main: "bootstrap.min"
	}, 
	{
	  name: "jquery",
	  location: "../assets",
	  main: "jquery-3.2.1.slim.min"
	}, 
	{
	  name: "popper.js",
	  location: "../assets",
	  main: "popper.min"
	}]
});

require(["codemirror", "codemirror/addon/mode/simple", "sapl-mode", "bootstrap"], function (CodeMirror, simpleMode, saplMode, bootstrap) {

	var editor = CodeMirror.fromTextArea(document.getElementById("policyTextArea"), {
	    lineNumbers: true,
	    mode: "xtext/sapl",
	    readOnly: "nocursor"
	  });
	
	{{replacement}}
	
	
	$(function () {
	  $('[data-toggle="popover"]').popover()
	})
});
