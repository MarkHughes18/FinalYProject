package com.example.backend.Authorisation;

import com.example.backend.Authorisation.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {
        try {
            auth.register(
                    req.getFullName(),
                    req.getEmail(),
                    req.getPassword(),
                    req.getDob());
            return ResponseEntity.ok(new AuthResponse(true, "Registered"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
        boolean ok = auth.login(req.getEmail(), req.getPassword());
        if (ok) {
            return ResponseEntity.ok(new AuthResponse(true, "Login successful"));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(false, "Invalid email or password"));
    }
}
