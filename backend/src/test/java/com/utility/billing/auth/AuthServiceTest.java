package com.utility.billing.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {
    private UserRepository users;
    private RefreshTokenRepository refreshTokens;
    private PasswordEncoder encoder;
    private JdbcTemplate jdbc;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        encoder = mock(PasswordEncoder.class);
        jdbc = mock(JdbcTemplate.class);
        auth = new AuthService(users, refreshTokens, encoder, mock(JwtService.class), jdbc, 7);
    }

    @Test
    void rejectsUserFromWrongPortalWithForbidden() {
        AppUser customer = new AppUser("customer@example.test", "hash", PortalRole.CUSTOMER, "ACTIVE");
        when(users.findByEmailIgnoreCaseAndStatusAndDeletedAtIsNull(anyString(), anyString())).thenReturn(Optional.of(customer));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> auth.login("customer@example.test", "password", PortalRole.ADMIN));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Invalid portal for this account", exception.getReason());
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void rejectsUnknownAccountWithoutRevealingPortal() {
        when(users.findByEmailIgnoreCaseAndStatusAndDeletedAtIsNull(anyString(), anyString())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> auth.login("missing@example.test", "password", PortalRole.ADMIN));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void explainsThatPendingCustomerRegistrationNeedsApproval() {
        AppUser pending = new AppUser("customer@example.test", "hash", PortalRole.CUSTOMER, "PENDING_APPROVAL");
        when(users.findByEmailIgnoreCaseAndStatusAndDeletedAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty(), Optional.of(pending));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> auth.login("customer@example.test", "password", PortalRole.CUSTOMER));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Customer registration is pending Admin approval", exception.getReason());
        verify(encoder, never()).matches(anyString(), anyString());
    }
}
