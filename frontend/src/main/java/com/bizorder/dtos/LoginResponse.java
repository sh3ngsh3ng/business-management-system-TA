package com.bizorder.dtos;

public class LoginResponse {
    private String token;

    private long expiresIn;

    // Getter and setter for token
    public String getToken() {
        return token;
    }

    public LoginResponse setToken(String token) {
        this.token = token;
        return this;
    }

    // Getter and setter for expiresIn
    public long getExpiresIn() {
        return expiresIn;
    }

    public LoginResponse setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
        return this;
    }
}
