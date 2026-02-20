package io.boomerang.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {
  private static final String MASTER_KEY =
      Base64.getEncoder().encodeToString(new byte[32]); // All zeros
  private EncryptionService encryptionService;

  @BeforeEach
  void setUp() {
    encryptionService = new EncryptionService(MASTER_KEY);
  }

  @Test
  void shouldEncryptAndDecrypt() {
    byte[] plainText = "Sensitive Data".getBytes();
    byte[] encrypted = encryptionService.encrypt(plainText);
    assertThat(encrypted).isNotEqualTo(plainText);

    byte[] decrypted = encryptionService.decrypt(encrypted);
    assertThat(decrypted).isEqualTo(plainText);
  }

  @Test
  void shouldProduceDifferentCiphertextForSamePlaintext() {
    byte[] plainText = "Sensitive Data".getBytes();
    byte[] encrypted1 = encryptionService.encrypt(plainText);
    byte[] encrypted2 = encryptionService.encrypt(plainText);

    assertThat(encrypted1).isNotEqualTo(encrypted2);

    assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(plainText);
    assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(plainText);
  }

  @Test
  void shouldThrowOnInvalidMasterKey() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThatThrownBy(() -> new EncryptionService(shortKey))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowOnDecryptionFailure() {
    byte[] plainText = "Sensitive Data".getBytes();
    byte[] encrypted = encryptionService.encrypt(plainText);
    encrypted[encrypted.length - 1] ^= 1; // Corrupt ciphertext

    assertThatThrownBy(() -> encryptionService.decrypt(encrypted))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("Decryption failed");
  }
}
