# An Editor for the SAPL DSL

Usage:

```java
public class JavabasedViewView extends Div {
    public JavabasedViewView() {
        setId("javabased-view-view");
        SaplEditor editor = new SaplEditor();
        add(editor);
        editor.addValueChangeListener(e -> System.out.println("value changed: "+e.isFromClient()+"  '"+e.getValue()+"'"));
        editor.setValue("policy \"set by Vaadin View after instantiation ->\\u2588<-\" permit");
    }
}
```