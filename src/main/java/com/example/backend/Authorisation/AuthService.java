package com.example.backend.Authorisation;

import com.example.backend.model.User;
import com.example.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository repo;

    public AuthService(UserRepository repo) {
        this.repo = repo;
    }

    public void register(String fullName, String email, String password, String dob) {
        if (repo.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt());

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(hash);
        user.setDob(dob);

        repo.save(user);
    }

    public boolean login(String email, String password) {
        return repo.findByEmail(email)
                .filter(u -> BCrypt.checkpw(password, u.getPasswordHash()))
                .isPresent();
    }
}
