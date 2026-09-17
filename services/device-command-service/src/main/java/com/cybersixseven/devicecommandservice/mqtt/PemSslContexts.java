package com.cybersixseven.devicecommandservice.mqtt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class PemSslContexts {

  private PemSslContexts() {}

  public static SSLContext fromPem(Path caCert, Path clientCert, Path clientKey) {
    try {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      X509Certificate ca = (X509Certificate) factory.generateCertificate(Files.newInputStream(caCert));
      Collection<? extends Certificate> certs =
          factory.generateCertificates(Files.newInputStream(clientCert));
      PrivateKey key = readPrivateKey(clientKey);

      KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
      trust.load(null, null);
      trust.setCertificateEntry("ca", ca);
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(trust);

      KeyStore keys = KeyStore.getInstance(KeyStore.getDefaultType());
      keys.load(null, null);
      keys.setKeyEntry(
          "client", key, new char[0], certs.toArray(Certificate[]::new));
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keys, new char[0]);

      SSLContext ssl = SSLContext.getInstance("TLS");
      ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
      return ssl;
    } catch (Exception ex) {
      throw new IllegalStateException("failed to build MQTT SSLContext from PEM files", ex);
    }
  }

  static PrivateKey readPrivateKey(Path path) throws Exception {
    String pem = Files.readString(path, StandardCharsets.UTF_8);
    String stripped = pem.replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(stripped);
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
    for (String algorithm : new String[] {"RSA", "EC"}) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(spec);
      } catch (InvalidKeySpecException ignored) {
        // try the next algorithm
      }
    }
    throw new IllegalArgumentException("unsupported private key in " + path);
  }
}
