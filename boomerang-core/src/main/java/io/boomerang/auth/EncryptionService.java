package io.boomerang.auth;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Service providing AES-256-GCM encryption and decryption.
 *
 * <p>Each encryption produces a random IV which is stored alongside the ciphertext.
 *
 * @since 1.0.0
 */
public class EncryptionService {
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int TAG_LENGTH_BIT = 128;
  private static final int IV_LENGTH_BYTE = 12;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecretKeySpec secretKey;

  /**
   * Constructs a new encryption service using the provided master key.
   *
   * @param masterKey the base64-encoded 256-bit master key; must be non-null
   * @throws IllegalArgumentException if the key is invalid
   */
  public EncryptionService(String masterKey) {
    if (masterKey == null || masterKey.isEmpty()) {
      throw new IllegalArgumentException("Master key must not be null or empty");
    }
    byte[] keyBytes = Base64.getDecoder().decode(masterKey);
    if (keyBytes.length != 32) {
      throw new IllegalArgumentException("Master key must be 256 bits (32 bytes)");
    }
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
  }

  /**
   * Encrypts the provided plain text.
   *
   * @param plainText the data to encrypt; must be non-null
   * @return the encrypted data (IV + ciphertext)
   * @throws RuntimeException if encryption fails
   */
  public byte[] encrypt(byte[] plainText) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTE];
      SECURE_RANDOM.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

      byte[] cipherText = cipher.doFinal(plainText);

      return ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
    } catch (GeneralSecurityException e) {
      throw new SecurityException("Encryption failed", e);
    }
  }

  /**
   * Decrypts the provided cipher text.
   *
   * @param cipherText the data to decrypt (IV + ciphertext); must be non-null
   * @return the decrypted data
   * @throws RuntimeException if decryption fails
   */
  public byte[] decrypt(byte[] cipherText) {
    try {
      ByteBuffer bb = ByteBuffer.wrap(cipherText);
      byte[] iv = new byte[IV_LENGTH_BYTE];
      bb.get(iv);
      byte[] actualCipherText = new byte[bb.remaining()];
      bb.get(actualCipherText);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

      return cipher.doFinal(actualCipherText);
    } catch (GeneralSecurityException e) {
      throw new SecurityException("Decryption failed", e);
    }
  }
}
