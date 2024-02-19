package io.sapl.demo.spring.data.r2dbc.data;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.sapl.demo.spring.data.r2dbc.model.Patient;
import io.sapl.demo.spring.data.r2dbc.model.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataGenerator {

    private final PatientRepository patientRepository;

    @EventListener(ContextRefreshedEvent.class)
    public void handleContextRefreshedEvent() {
        Hooks.onOperatorDebug();
        generateData();
    }

    @SneakyThrows
    private void generateData() {
        patientRepository.count().flatMap(count -> count == 0L ? generatePatients() : Mono.empty()).subscribe();
    }

    private Mono<Void> generatePatients() {
        log.info("No data found. Generating...");
        return patientRepository.save(new Patient("Billy", "the Kid", "Latent need for shooting people.")).then();
    }

}
