package com.example.finalyearproject.data;

public class RegisterRequest {
    private String fullName, email, password, dob;
    public RegisterRequest(String fullName, String email, String password, String dob){
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.dob = dob;
    }
}
