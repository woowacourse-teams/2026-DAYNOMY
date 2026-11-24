package org.grit.daynomy.portfolio.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortfolioShareTokenCipher {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final String VERSION = "v1.";
  private static final byte[] AAD =
      "daynomy:portfolio-analysis-share:v1".getBytes(StandardCharsets.UTF_8);
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public PortfolioShareTokenCipher(
      @Value("${portfolio.analysis.share-token-encryption-secret}") String secret) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("포트폴리오 공유 토큰 암호화 키는 32바이트 이상이어야 합니다.");
    }
    this.key = new SecretKeySpec(deriveKey(secret), "AES");
  }

  public String encrypt(String token) {
    if (token == null || token.isBlank() || token.length() > 100) {
      throw new IllegalArgumentException("암호화할 공유 토큰 형식이 올바르지 않습니다.");
    }
    byte[] iv = new byte[IV_LENGTH];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      cipher.updateAAD(AAD);
      byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
      byte[] payload =
          ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
      return VERSION + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("포트폴리오 공유 토큰을 암호화할 수 없습니다.", exception);
    }
  }

  public String decrypt(String encryptedToken) {
    if (encryptedToken == null || !encryptedToken.startsWith(VERSION)) {
      throw new IllegalStateException("암호화된 공유 토큰 형식이 올바르지 않습니다.");
    }
    try {
      byte[] encoded = Base64.getUrlDecoder().decode(encryptedToken.substring(VERSION.length()));
      if (encoded.length <= IV_LENGTH) {
        throw new IllegalArgumentException("암호화된 공유 토큰 형식이 올바르지 않습니다.");
      }
      ByteBuffer buffer = ByteBuffer.wrap(encoded);
      byte[] iv = new byte[IV_LENGTH];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      cipher.updateAAD(AAD);
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("포트폴리오 공유 토큰을 복호화할 수 없습니다.", exception);
    }
  }

  private static byte[] deriveKey(String secret) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(("portfolio-share-token:" + secret).getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
    }
  }
}
