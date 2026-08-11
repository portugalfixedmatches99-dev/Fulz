package com.fulizaboost.controller;

import com.fulizaboost.entity.User;
import com.fulizaboost.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String email    = (String) body.get("email");
        String phone    = (String) body.get("phone");
        String country  = (String) body.getOrDefault("country", "Kenya");
        String password = (String) body.get("password");

        if (username == null || email == null || password == null || phone == null)
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));

        if (userRepository.existsByEmail(email))
            return ResponseEntity.status(409).body(Map.of("error", "Email already registered"));

        if (userRepository.existsByUsername(username))
            return ResponseEntity.status(409).body(Map.of("error", "Username already taken"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setCountry(country);
        user.setPassword(password);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Account created", "user", safeUser(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        String email    = (String) body.get("email");
        String password = (String) body.get("password");

        if (email == null || password == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password required"));

        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty() || !found.get().getPassword().equals(password))
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));

        return ResponseEntity.ok(Map.of("message", "Login successful", "user", safeUser(found.get())));
    }

    private Map<String, Object> safeUser(User u) {
        return Map.of(
            "id",          u.getId(),
            "username",    u.getUsername(),
            "email",       u.getEmail(),
            "phone",       u.getPhone(),
            "country",     u.getCountry(),
            "balance",     u.getBalance(),
            "totalEarned", u.getTotalEarned()
        );
    }





}
