// File: C:\Projects\EchoU\backend\src\main\java\com\example\config\CustomUserDetailsService.java

package com.example.config;

import com.example.entity.User;
import com.example.entity.Parent; // CHANGE: Added import
import com.example.repository.UserRepository;
import com.example.repository.ParentRepository; // CHANGE: Added import
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParentRepository parentRepository; // CHANGE: Added

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user != null) {
            return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                   .password(user.getPassword())
                   .authorities("USER")
                   .build();
        }
        // CHANGE: Check for parent if user not found
        Parent parent = parentRepository.findByUsername(username);
        if (parent != null) {
            return org.springframework.security.core.userdetails.User.withUsername(parent.getUsername())
                   .password(parent.getPassword())
                   .authorities("PARENT")
                   .build();
        }
        throw new UsernameNotFoundException("User not found: " + username);
    }
}