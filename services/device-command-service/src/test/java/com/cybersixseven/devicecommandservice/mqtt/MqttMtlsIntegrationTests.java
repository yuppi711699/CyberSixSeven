package com.cybersixseven.devicecommandservice.mqtt;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.devicecommandservice.support.MqttTestCertificates;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

class MqttMtlsIntegrationTests {

  private static Path certs;
  private static GenericContainer<?> mosquitto;

  @BeforeAll
  static void startBroker() throws Exception {
    certs = Files.createTempDirectory("c67-mqtt-certs");
    MqttTestCertificates.generate(certs);
    Files.writeString(
        certs.resolve("mosquitto.conf"),
        """
        listener 8883
        protocol mqtt
        allow_anonymous false
        require_certificate true
        use_identity_as_username true
        cafile /mosquitto/certs/ca.crt
        certfile /mosquitto/certs/broker.crt
        keyfile /mosquitto/certs/broker.key
        """);
    mosquitto = new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2"))
        .withExposedPorts(8883)
        .withCopyFileToContainer(
            MountableFile.forHostPath(certs.resolve("mosquitto.conf"), 0444),
            "/mosquitto/config/mosquitto.conf")
        .withCopyFileToContainer(
            MountableFile.forHostPath(certs.resolve("ca.crt"), 0444),
            "/mosquitto/certs/ca.crt")
        .withCopyFileToContainer(
            MountableFile.forHostPath(certs.resolve("broker.crt"), 0444),
            "/mosquitto/certs/broker.crt")
        .withCopyFileToContainer(
            MountableFile.forHostPath(certs.resolve("broker.key"), 0444),
            "/mosquitto/certs/broker.key")
        .waitingFor(Wait.forListeningPort());
    mosquitto.start();
  }

  @AfterAll
  static void stopBroker() {
    if (mosquitto != null) {
      mosquitto.stop();
    }
  }

  @Test
  void serviceCertificateCanPublishAndUnauthenticatedClientIsRejected() throws Exception {
    String url = "ssl://%s:%d".formatted(mosquitto.getHost(), mosquitto.getMappedPort(8883));
    SSLContext ssl = PemSslContexts.fromPem(
        certs.resolve("ca.crt"), certs.resolve("paho.crt"), certs.resolve("paho.key"));

    MqttConnectionOptions options = new MqttConnectionOptions();
    options.setConnectionTimeout(10);
    options.setAutomaticReconnect(false);
    options.setHttpsHostnameVerificationEnabled(true);
    options.setSocketFactory(ssl.getSocketFactory());

    CountDownLatch latch = new CountDownLatch(1);
    MqttClient client = new MqttClient(url, "paho-test", new MemoryPersistence());
    try {
      client.setCallback(new MqttCallback() {
        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {}

        @Override
        public void mqttErrorOccurred(MqttException exception) {}

        @Override
        public void messageArrived(String topic, MqttMessage message) {
          latch.countDown();
        }

        @Override
        public void deliveryComplete(IMqttToken token) {}

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {}

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {}
      });
      client.connectWithResult(options).waitForCompletion(Duration.ofSeconds(10).toMillis());
      client.subscribe("devices/esp32-dev-001/commands", 1);
      MqttMessage message = new MqttMessage(
          "{\"commandId\":\"c\",\"event\":\"correct\",\"intensity\":3,\"submissionId\":\"s\"}"
              .getBytes(StandardCharsets.UTF_8));
      message.setQos(1);
      client.publish("devices/esp32-dev-001/commands", message);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
    } finally {
      if (client.isConnected()) {
        client.disconnect();
      }
      client.close();
    }

    MqttConnectionOptions noClient = new MqttConnectionOptions();
    noClient.setConnectionTimeout(5);
    noClient.setHttpsHostnameVerificationEnabled(true);
    noClient.setSocketFactory(trustOnly(certs.resolve("ca.crt")).getSocketFactory());
    MqttClient rejected = new MqttClient(url, "no-cert", new MemoryPersistence());
    try {
      assertThrows(MqttException.class, () -> rejected.connect(noClient));
    } finally {
      rejected.close();
    }
  }

  private static SSLContext trustOnly(Path ca) throws Exception {
    var factory = java.security.cert.CertificateFactory.getInstance("X.509");
    var cert = (java.security.cert.X509Certificate) factory.generateCertificate(Files.newInputStream(ca));
    var trust = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
    trust.load(null, null);
    trust.setCertificateEntry("ca", cert);
    var tmf = javax.net.ssl.TrustManagerFactory.getInstance(
        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trust);
    SSLContext ssl = SSLContext.getInstance("TLS");
    ssl.init(null, tmf.getTrustManagers(), null);
    return ssl;
  }
}
