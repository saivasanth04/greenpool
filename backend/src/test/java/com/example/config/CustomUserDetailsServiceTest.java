package com.example.config;

import com.example.entity.User;
import com.example.entity.Parent;
import com.example.repository.UserRepository;
import com.example.repository.ParentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ParentRepository parentRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void loadUserByUsername_UserFound() {
        User mockUser = new User();
        mockUser.setUsername("student1");
        mockUser.setPassword("{noop}pass123");

        when(userRepository.findByUsername("student1")).thenReturn(mockUser);

        var userDetails = customUserDetailsService.loadUserByUsername("student1");

        assertNotNull(userDetails);
        assertEquals("student1", userDetails.getUsername());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("USER")));
    }

    @Test
    void loadUserByUsername_ParentFound() {
        Parent mockParent = new Parent();
        mockParent.setUsername("parent1");
        mockParent.setPassword("{noop}pass456");

        when(userRepository.findByUsername("parent1")).thenReturn(null);
        when(parentRepository.findByUsername("parent1")).thenReturn(mockParent);

        var userDetails = customUserDetailsService.loadUserByUsername("parent1");

        assertNotNull(userDetails);
        assertEquals("parent1", userDetails.getUsername());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("PARENT")));
    }

    @Test
    void loadUserByUsername_NotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(null);
        when(parentRepository.findByUsername("unknown")).thenReturn(null);

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("unknown"));
    }
}