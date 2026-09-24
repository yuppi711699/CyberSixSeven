package com.cybersixseven.devicecommandservice.mqtt;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersixseven.devicecommandservice.support.MqttTestCertificates;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PemSslContextsTests {

  @TempDir
  Path tempDir;

  @Test
  void buildsTlsContextFromGeneratedPems() throws Exception {
    MqttTestCertificates.generate(tempDir);
    SSLContext ssl = PemSslContexts.fromPem(
        tempDir.resolve("ca.crt"), tempDir.resolve("paho.crt"), tempDir.resolve("paho.key"));
    assertNotNull(ssl.getSocketFactory());
  }

  @Test
  void missingPemFailsFast() {
    assertThrows(
        IllegalStateException.class,
        () -> PemSslContexts.fromPem(
            Path.of("/tmp/missing-ca.crt"),
            Path.of("/tmp/missing-client.crt"),
            Path.of("/tmp/missing-client.key")));
  }

  @Test
  void unsupportedKeyMaterialIsRejected() throws Exception {
    Path junk = tempDir.resolve("junk.key");
    Files.writeString(junk, "-----BEGIN PRIVATE KEY-----\nQQ==\n-----END PRIVATE KEY-----\n");
    assertThrows(Exception.class, () -> PemSslContexts.readPrivateKey(junk));
  }
}
