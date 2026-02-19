package io.boomerang.auth;

import io.boomerang.model.Client;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private final Map<String, Client> clients = new ConcurrentHashMap<>();
  private static final int ITERATIONS = 10000;
  private static final int KEY_LENGTH = 256;
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

  public void registerClient(String clientId, String password, boolean isAdmin) {
    String hashedPassword = hashPassword(password);
    clients.put(clientId, new Client(clientId, hashedPassword, isAdmin));
    log.info("Registered client: {} (Admin: {})", clientId, isAdmin);
  }

  public boolean registerClientByAdmin(
      String adminClientId, String newClientId, String newPassword, boolean isNewAdmin) {
    Optional<Client> admin = getClient(adminClientId);
    if (admin.isEmpty() || !admin.get().isAdmin()) {
      log.warn("Unauthorized registration attempt of '{}' by '{}'", newClientId, adminClientId);
      return false;
    }

    registerClient(newClientId, newPassword, isNewAdmin);
    return true;
  }

  public boolean authenticate(String clientId, String password) {
    Client client = clients.get(clientId);
    if (client == null) {
      return false;
    }
    return verifyPassword(password, client.hashedPassword());
  }

  public Optional<Client> getClient(String clientId) {
    return Optional.ofNullable(clients.get(clientId));
  }

  private String hashPassword(String password) {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    byte[] hash = pbkdf2(password.toCharArray(), salt);
    return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
  }

  private boolean verifyPassword(String password, String storedHash) {
    String[] parts = storedHash.split(":");
    if (parts.length != 2) {
      return false;
    }
    byte[] salt = Base64.getDecoder().decode(parts[0]);
    byte[] hash = Base64.getDecoder().decode(parts[1]);
    byte[] testHash = pbkdf2(password.toCharArray(), salt);
    return java.util.Arrays.equals(hash, testHash);
  }

  private byte[] pbkdf2(char[] password, byte[] salt) {
    PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
    try {
      SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
      return factory.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new RuntimeException("Error hashing password", e);
    }
  }
}
