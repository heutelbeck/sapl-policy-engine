//package integration;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.boot.test.util.TestPropertyValues;
//import org.springframework.context.ApplicationContextInitializer;
//import org.springframework.context.ConfigurableApplicationContext;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.test.context.ContextConfiguration;
//import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
//import org.testcontainers.containers.PostgreSQLContainer;
//
//import io.sapl.springdatar2dbc.database.PersonR2dbcRepository;
//import reactor.test.StepVerifier;
//
//@SpringBootTest(classes = PersonR2dbcRepository.class)
//class PersonR2dbcRepositoryIntegrationTests {
//
//	@Autowired
//	private PersonR2dbcRepository personR2dbcRepository;
//	
////    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:latest")
////            .withDatabaseName("test")
////            .withUsername("test")
////            .withPassword("test");
////
////    static {
////        POSTGRES_CONTAINER.start();
////    }
//
//	@Test
//	void testSaveUser() {
//		// GIVEN
//
//		// WHEN
//
//		StepVerifier.create(personR2dbcRepository.findAll()).expectNextCount(0).verifyComplete();
//
//		// THEN
//	}
//
//}
