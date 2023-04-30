package io.sapl.vaadin;

public class EditorClickedEvent {

    private final Integer line;
    private final String content;

    public EditorClickedEvent(Integer line, String content) {
        this.line = line;
        this.content = content;
    }

    public Integer getLine() {
        return this.line;
    }
    public String getContent() { return this.content; }

}
