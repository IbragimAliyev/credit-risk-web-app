package com.creditrisk.auth;

import com.creditrisk.auth.AuthDtos.AuthResponse;
import com.creditrisk.auth.AuthDtos.LoginRequest;
import com.creditrisk.auth.AuthDtos.RegisterRequest;
import com.creditrisk.auth.AuthDtos.UserInfo;
import com.creditrisk.exception.ConflictException;
import com.creditrisk.security.JwtService;
import com.creditrisk.security.UserPrincipal;
import com.creditrisk.user.User;
import com.creditrisk.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = userRepository.save(
                new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
        return toResponse(new UserPrincipal(user));
    }

    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException on failure; handled globally as 401.
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim(), request.password()));
        return toResponse((UserPrincipal) auth.getPrincipal());
    }

    private AuthResponse toResponse(UserPrincipal user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                new UserInfo(user.getId(), user.getName(), user.getEmail()));
    }
}
