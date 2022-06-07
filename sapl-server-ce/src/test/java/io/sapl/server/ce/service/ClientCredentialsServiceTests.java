package io.sapl.server.ce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.sapl.server.ce.model.ClientCredentials;
import io.sapl.server.ce.persistence.ClientCredentialsRepository;
import reactor.util.function.Tuple2;

@ExtendWith(MockitoExtension.class)
public class ClientCredentialsServiceTests {
    private ClientCredentialsRepository clientCredentialsRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void beforeEach() {
        clientCredentialsRepository = mock(ClientCredentialsRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
    }

    @Test
    public void loadUserByUsername() {
        final String userName = "user";
        final String encodedSecret = "encoded";

        ClientCredentials clientCredentials = new ClientCredentials();
        clientCredentials.setKey("id");
        clientCredentials.setKey(userName);
        clientCredentials.setEncodedSecret(encodedSecret);
        when(clientCredentialsRepository.findByKey(userName)).thenReturn(Collections.singletonList(clientCredentials));

        ClientCredentialsService clientCredentialsService = getClientCredentialsService();
        UserDetails springUserDetails = clientCredentialsService.loadUserByUsername(userName);

        assertEquals(userName, springUserDetails.getUsername());
        assertEquals(encodedSecret, springUserDetails.getPassword());
    }

    @Test
    public void loadUserByUsername_unknownUsername() {
        final String unknownUserName = "foo";

        when(clientCredentialsRepository.findByKey(unknownUserName)).thenReturn(Collections.emptyList());

        ClientCredentialsService clientCredentialsService = getClientCredentialsService();
        Assertions.assertThrows(UsernameNotFoundException.class, () -> {
            clientCredentialsService.loadUserByUsername(unknownUserName);
        });
    }

    @Test
    public void loadUserByUsername_moreThanOneMatchingClient() {
        final String userName = "user";
        final String encodedSecret = "encoded";

        ClientCredentials clientCredentials = new ClientCredentials();
        clientCredentials.setKey("id");
        clientCredentials.setKey(userName);
        clientCredentials.setEncodedSecret(encodedSecret);
        when(clientCredentialsRepository.findByKey(userName)).thenReturn(Arrays.asList(clientCredentials, clientCredentials));

        ClientCredentialsService clientCredentialsService = getClientCredentialsService();
        UserDetails springUserDetails = clientCredentialsService.loadUserByUsername(userName);

        assertEquals(userName, springUserDetails.getUsername());
        assertEquals(encodedSecret, springUserDetails.getPassword());
    }

    @Test
    public void getAll() {
        Collection<ClientCredentials> expectedClientCredentials, actualClientCredentials;
        ClientCredentialsService clientCredentialsService = getClientCredentialsService();

        expectedClientCredentials = Collections.emptyList();
        when(clientCredentialsRepository.findAll()).thenReturn(expectedClientCredentials);
        actualClientCredentials = clientCredentialsService.getAll();
        assertEquals(expectedClientCredentials, actualClientCredentials);

        expectedClientCredentials = Collections.singletonList(new ClientCredentials());
        when(clientCredentialsRepository.findAll()).thenReturn(expectedClientCredentials);
        actualClientCredentials = clientCredentialsService.getAll();
        assertEquals(expectedClientCredentials, actualClientCredentials);

        expectedClientCredentials = Arrays.asList(new ClientCredentials(), new ClientCredentials());
        when(clientCredentialsRepository.findAll()).thenReturn(expectedClientCredentials);
        actualClientCredentials = clientCredentialsService.getAll();
        assertEquals(expectedClientCredentials, actualClientCredentials);
    }

    @Test
    public void getAmount() {
        ClientCredentialsService clientCredentialsService = getClientCredentialsService();

        when(clientCredentialsRepository.count()).thenReturn(Long.valueOf(0));
        assertEquals(0, clientCredentialsService.getAmount());

        when(clientCredentialsRepository.count()).thenReturn(Long.valueOf(1));
        assertEquals(1, clientCredentialsService.getAmount());

        when(clientCredentialsRepository.count()).thenReturn(Long.valueOf(2));
        assertEquals(2, clientCredentialsService.getAmount());
    }

    @Test
    public void createDefault() {
        final String encodedSecret = "encoded";

        Tuple2<ClientCredentials, String> clientCredentialsWithSecret;
        ClientCredentialsService clientCredentialsService = getClientCredentialsService();

        when(clientCredentialsRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(passwordEncoder.encode(any())).thenAnswer(i -> encodedSecret);
        clientCredentialsWithSecret = clientCredentialsService.createDefault();
        verify(clientCredentialsRepository, times(1)).save(any());
        verify(passwordEncoder, times(1)).encode(any());
        assertEquals(encodedSecret, clientCredentialsWithSecret.getT1().getEncodedSecret());
        assertEquals(encodedSecret, passwordEncoder.encode(clientCredentialsWithSecret.getT2()));
    }

    @Test
    public void delete() {
        final long id = 19;

        ClientCredentialsService clientCredentialsService = getClientCredentialsService();

        Assertions.assertThrows(NullPointerException.class, () -> {
            clientCredentialsService.delete(null);
        });

        clientCredentialsService.delete(id);
        verify(clientCredentialsRepository, times(1)).deleteById(id);
    }

    @Test
    public void encodeSecret() {
        final String encoded = "encoded";

        ClientCredentialsService clientCredentialsService = getClientCredentialsService();

        Assertions.assertThrows(NullPointerException.class, () -> {
            clientCredentialsService.encodeSecret(null);
        });

        when(passwordEncoder.encode(any())).thenAnswer(i -> encoded);
        assertEquals(encoded, clientCredentialsService.encodeSecret("secret"));
        verify(passwordEncoder, times(1)).encode(any());
    }

    private ClientCredentialsService getClientCredentialsService() {
        return new ClientCredentialsService(clientCredentialsRepository, passwordEncoder);
    }
}
