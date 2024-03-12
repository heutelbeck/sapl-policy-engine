package io.sapl.languageserver;

import org.eclipse.xtext.ide.server.ServerLauncher;
import org.eclipse.xtext.ide.server.ServerModule;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class LanguageServerStartup {

    private final TaskExecutor executor;

    public LanguageServerStartup(TaskExecutor executor) {
        this.executor = executor;
    }

    @EventListener
    public void runServer(ContextRefreshedEvent cre) {
        executor.execute(
                () -> ServerLauncher.launch(SAPLLanguageServer.class.getName(), new String[] {}, new ServerModule()));
    }
}
