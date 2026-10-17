package com.gabrielf.payment_api.service;

import com.gabrielf.payment_api.dto.RegisterRequest;
import com.gabrielf.payment_api.exception.EmailAlreadyUsedException;
import com.gabrielf.payment_api.model.User;
import com.gabrielf.payment_api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    private static final String EMAIL = "ana@teste.com";
    private static final String PASSWORD = "12345678";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void register_shouldSaveHashedPassword_whenEmailIsNew() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");

        userService.register(new RegisterRequest(EMAIL, PASSWORD));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(captor.getValue().getPasswordHash())
                .isEqualTo("hashed-password")
                .isNotEqualTo(PASSWORD);


    }

    @Test
    void register_shouldThrow_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> userService.register(new RegisterRequest(EMAIL, PASSWORD)))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .hasMessageContaining(EMAIL);

        verify(userRepository, never()).saveAndFlush(any());

    }

    @Test
    void register_shouldThrowEmailAlreadyUsed_whenConcurrentInsertViolatesUniqueEmail() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> userService.register(new RegisterRequest(EMAIL, PASSWORD)))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .hasMessageContaining(EMAIL);

    }

}
