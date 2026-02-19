package io.boomerang.model;

public record Client(String clientId, String hashedPassword, boolean isAdmin) {}
