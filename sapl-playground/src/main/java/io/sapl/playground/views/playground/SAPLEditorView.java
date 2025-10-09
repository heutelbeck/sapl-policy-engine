package io.sapl.playground.views.playground;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import io.sapl.vaadin.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PageTitle("SAPL Editor")
@Route(value = "sapl")
public class SAPLEditorView extends VerticalLayout {

    private static final String DEFAULT_LEFT = """
            policy "x"
            permit
            where
              subject == {"role":"author"};
            obligation
              {
                "log":"access-granted"
              }
            """;

    private static final String DEFAULT_RIGHT = """
            policy "x"
            deny
            where
              subject == {"role":"manager"};
            obligation
              {
                "log":"access-granted"
              }
            obligation
              {
                "log":"manager access-granted"
              }
            """;

    private final SaplEditor saplEditor;
    private Button           toggleMerge;

    // track toggle state
    private boolean mergeEnabled = true;

    public SAPLEditorView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        final var cfg = new SaplEditorConfiguration();
        cfg.setHasLineNumbers(true);
        cfg.setTextUpdateDelay(400);
        cfg.setDarkTheme(true);

        saplEditor = new SaplEditor(cfg);
        saplEditor.setWidthFull();
        saplEditor.setHeight("70vh");

        saplEditor.addDocumentChangedListener(this::onDocumentChanged);
        saplEditor.addValidationFinishedListener(this::onValidationFinished);

        final var mergeControls    = buildMergeControls();
        final var coverageControls = buildCoverageControls();

        add(saplEditor, mergeControls, coverageControls);

        // Editor initialization
        saplEditor.setDocument(DEFAULT_LEFT);
        saplEditor.setConfigurationId("1");
        saplEditor.setDocument(DEFAULT_LEFT);
        saplEditor.setMergeRightContent(DEFAULT_RIGHT);
        saplEditor.setMergeOption("showDifferences", true);
        saplEditor.setMergeOption("revertButtons", true);
        saplEditor.setMergeOption("connect", null);
        saplEditor.setMergeOption("collapseIdentical", false);
        saplEditor.setMergeOption("allowEditingOriginals", false);
        saplEditor.setMergeOption("ignoreWhitespace", false);

        saplEditor.setMergeModeEnabled(mergeEnabled); // last
    }

    // -------------------- MERGE CONTROLS --------------------

    private HorizontalLayout buildMergeControls() {
        final var bar = new HorizontalLayout();
        bar.setWidthFull();
        bar.setAlignItems(Alignment.CENTER);
        bar.setSpacing(true);
        bar.getStyle().set("padding", "0.5rem 1rem");

        this.toggleMerge = new Button(mergeEnabled ? "Disable Merge" : "Enable Merge", this::toggleMerge);

        final var setRight   = new Button("Set Right Sample", e -> saplEditor.setMergeRightContent(DEFAULT_RIGHT));
        final var clearRight = new Button("Clear Right", e -> saplEditor.setMergeRightContent(""));

        final var prev = new Button("Prev Change", e -> saplEditor.goToPreviousChange());
        final var next = new Button("Next Change", e -> saplEditor.goToNextChange());

        final var showDiff = new Checkbox("Show differences", true);
        showDiff.addValueChangeListener(
                e -> saplEditor.setMergeOption("showDifferences", Boolean.TRUE.equals(e.getValue())));

        final var revertBtns = new Checkbox("Revert buttons", true);
        revertBtns.addValueChangeListener(
                e -> saplEditor.setMergeOption("revertButtons", Boolean.TRUE.equals(e.getValue())));

        final var connect = new ComboBox<String>("Connectors");
        connect.setItems("none", "align");
        connect.setValue("none");
        connect.addValueChangeListener(
                e -> saplEditor.setMergeOption("connect", "align".equals(e.getValue()) ? "align" : null));

        final var collapse = new Checkbox("Collapse identical", false);
        collapse.addValueChangeListener(
                e -> saplEditor.setMergeOption("collapseIdentical", Boolean.TRUE.equals(e.getValue())));

        final var allowEditOrig = new Checkbox("Allow editing right", false);
        allowEditOrig.addValueChangeListener(
                e -> saplEditor.setMergeOption("allowEditingOriginals", Boolean.TRUE.equals(e.getValue())));

        final var ignoreWs = new Checkbox("Ignore whitespace", false);
        ignoreWs.addValueChangeListener(
                e -> saplEditor.setMergeOption("ignoreWhitespace", Boolean.TRUE.equals(e.getValue())));

        final var readOnly = new Checkbox("Read-only left", false);
        readOnly.addValueChangeListener(e -> saplEditor.setReadOnly(Boolean.TRUE.equals(e.getValue())));

        final var dark = new Checkbox("Dark theme", true);
        dark.addValueChangeListener(e -> saplEditor.setDarkTheme(Boolean.TRUE.equals(e.getValue())));

        final var configId = new IntegerField("Configuration Id");
        configId.setStepButtonsVisible(true);
        configId.setMin(1);
        configId.setMax(5);
        configId.setValue(1);
        configId.addValueChangeListener(e -> {
            if (e.getValue() != null)
                saplEditor.setConfigurationId(e.getValue().toString());
        });

        final var setDefault = new Button("Set Doc Default", e -> saplEditor.setDocument(DEFAULT_LEFT));
        final var showDoc    = new Button("Show Doc in Console", e -> log.info("SAPL: {}", saplEditor.getDocument()));

        final var filler = new FlexLayout();
        filler.setFlexGrow(1, filler);

        bar.add(toggleMerge, setRight, clearRight, prev, next, showDiff, revertBtns, connect, collapse, allowEditOrig,
                ignoreWs, readOnly, dark, configId, setDefault, showDoc, filler);
        return bar;
    }

    private void toggleMerge(ClickEvent<Button> e) {
        mergeEnabled = !mergeEnabled;
        saplEditor.setMergeModeEnabled(mergeEnabled);
        toggleMerge.setText(mergeEnabled ? "Disable Merge" : "Enable Merge");
    }

    // -------------------- COVERAGE CONTROLS --------------------

    private HorizontalLayout buildCoverageControls() {
        final var bar = new HorizontalLayout();
        bar.setWidthFull();
        bar.setAlignItems(Alignment.CENTER);
        bar.setSpacing(true);
        bar.getStyle().set("padding", "0.25rem 1rem");

        // Apply demo coverage: show all states on distinct lines
        final var applyAll = new Button("Apply Coverage (All States)", e -> applyAllStatesCoverage());

        // Apply a sample “branch counts” style like your report (adds tooltips)
        final var applySample = new Button("Apply Sample Coverage", e -> applySampleCoverage());

        // Toggle: auto-clear on edit (default true in JS)
        final var autoClear = new Checkbox("Auto-clear on edit", true);
        autoClear.addValueChangeListener(
                e -> saplEditor.getElement().callJsFunction("setCoverageAutoClear", Boolean.TRUE.equals(e.getValue())));

        // Toggle: show ignored (gray) lines
        final var showIgnored = new Checkbox("Show ignored", false);
        showIgnored.addValueChangeListener(e -> saplEditor.getElement().callJsFunction("setCoverageShowIgnored",
                Boolean.TRUE.equals(e.getValue())));

        // Clear coverage
        final var clear = new Button("Clear Coverage", e -> saplEditor.getElement().callJsFunction("clearCoverage"));

        final var filler = new FlexLayout();
        filler.setFlexGrow(1, filler);

        bar.add(applyAll, applySample, autoClear, showIgnored, clear, filler);
        return bar;
    }

    // -------------------- COVERAGE PAYLOADS --------------------

    /**
     * Small demo that exercises COVERED, PARTIAL, UNCOVERED, IGNORED on visible
     * lines.
     */
    private void applyAllStatesCoverage() {
        // Choose lines that exist in DEFAULT_LEFT (1-based)
        // 1: policy "x"
        // 2: permit
        // 3: where
        // 4: subject == ...
        // 5: obligation
        // 6: {
        // 7: "log":"access-granted"
        // 8: }
        // 9: (empty)
        JsonObject payload = Json.createObject();
        JsonArray  lines   = Json.createArray();

        int i = 0;

        // green
        lines.set(i++, lineEntry(1, "COVERED", "Covered by tests"));
        // yellow
        lines.set(i++, lineEntry(4, "PARTIAL", "1 of 2 branches covered"));
        // red
        lines.set(i++, lineEntry(2, "UNCOVERED", "0 of 1 branches covered"));
        // ignored (only visible if 'Show ignored' is checked)
        lines.set(i, lineEntry(7, "IGNORED", "No tests target this line"));

        payload.put("lines", lines);

        saplEditor.getElement().callJsFunction("setCoverageData", payload);
    }

    /** Example resembling the report-style tooltips (branch info). */
    private void applySampleCoverage() {
        JsonObject payload = Json.createObject();
        JsonArray  lines   = Json.createArray();
        int        i       = 0;

        lines.set(i++, lineEntry(1, "COVERED", null)); // policy "x"
        lines.set(i++, lineEntry(2, "COVERED", null)); // permit
        lines.set(i++, lineEntry(4, "PARTIAL", "1 of 2 branches covered")); // subject == ...
        lines.set(i++, lineEntry(5, "IGNORED", "No decision impact"));      // obligation
        lines.set(i++, lineEntry(7, "COVERED", null));                      // "log": ...
        lines.set(i, lineEntry(3, "UNCOVERED", "0 of 1 branches covered"));// where

        payload.put("lines", lines);

        saplEditor.getElement().callJsFunction("setCoverageData", payload);
    }

    private static JsonObject lineEntry(int oneBasedLine, String status, String info) {
        JsonObject o = Json.createObject();
        o.put("line", oneBasedLine);
        o.put("status", status);
        if (info != null)
            o.put("info", info);
        return o;
    }

    // -------------------- EVENTS --------------------

    private void onDocumentChanged(DocumentChangedEvent event) {
        log.info("SAPL value changed: {}", event.getNewValue());
    }

    private void onValidationFinished(ValidationFinishedEvent event) {
        final Issue[] issues = event.getIssues();
        log.info("validation finished, number of issues: {}", issues.length);
        for (Issue issue : issues) {
            log.info(" - {}", issue.getDescription());
        }
    }
}
