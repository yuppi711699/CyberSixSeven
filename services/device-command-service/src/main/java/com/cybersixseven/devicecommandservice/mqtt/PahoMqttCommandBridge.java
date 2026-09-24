package com.cybersixseven.devicecommandservice.mqtt;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventStore;
import com.cybersixseven.devicecommandservice.heartbeat.PlatformHeartbeatClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true")
public class PahoMqttCommandBridge
    implements MqttCommandPublisher, MqttCallback, InitializingBean, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(PahoMqttCommandBridge.class);
  private static final String STATUS_TOPIC = "devices/+/status";

  private final DeviceEventStore deviceEventStore;
  private final PlatformHeartbeatClient heartbeatClient;
  private final ObjectMapper objectMapper;
  private final String brokerUrl;
  private final String clientId;
  private final Path caCert;
  private final Path clientCert;
  private final Path clientKey;
  private final int connectTimeoutSeconds;
  private final long completionTimeoutMs;
  private MqttAsyncClient client;

  public PahoMqttCommandBridge(
      DeviceEventStore deviceEventStore,
      PlatformHeartbeatClient heartbeatClient,
      ObjectMapper objectMapper,
      @Value("${app.mqtt.broker-url}") String brokerUrl,
      @Value("${app.mqtt.client-id}") String clientId,
      @Value("${app.mqtt.ca-cert-path}") String caCertPath,
      @Value("${app.mqtt.client-cert-path}") String clientCertPath,
      @Value("${app.mqtt.client-key-path}") String clientKeyPath,
      @Value("${app.mqtt.connect-timeout-seconds}") int connectTimeoutSeconds,
      @Value("${app.mqtt.completion-timeout-seconds}") int completionTimeoutSeconds) {
    this.deviceEventStore = deviceEventStore;
    this.heartbeatClient = heartbeatClient;
    this.objectMapper = objectMapper;
    this.brokerUrl = brokerUrl;
    this.clientId = clientId;
    this.caCert = Path.of(caCertPath);
    this.clientCert = Path.of(clientCertPath);
    this.clientKey = Path.of(clientKeyPath);
    this.connectTimeoutSeconds = connectTimeoutSeconds;
    this.completionTimeoutMs = completionTimeoutSeconds * 1000L;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    MqttConnectionOptions options = new MqttConnectionOptions();
    options.setConnectionTimeout(connectTimeoutSeconds);
    options.setAutomaticReconnect(true);
    options.setCleanStart(true);
    options.setHttpsHostnameVerificationEnabled(true);
    options.setSocketFactory(PemSslContexts.fromPem(caCert, clientCert, clientKey).getSocketFactory());
    client = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());
    client.setCallback(this);
    client.connect(options).waitForCompletion(completionTimeoutMs);
    client.subscribe(STATUS_TOPIC, 1).waitForCompletion(completionTimeoutMs);
    log.info("mqtt connected broker={} clientId={}", brokerUrl, clientId);
  }

  @Override
  public void publishCommand(DeviceCommandMessage command) {
    if (client == null || !client.isConnected()) {
      throw new IllegalStateException("mqtt client is not connected");
    }
    try {
      String payload = objectMapper.writeValueAsString(new MqttCommandPayload(
          command.commandId().toString(),
          command.event(),
          command.intensity(),
          command.submissionId().toString()));
      MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
      message.setQos(1);
      message.setRetained(false);
      client.publish(command.commandTopic(), message).waitForCompletion(completionTimeoutMs);
      log.info(
          "mqtt published commandId={} submissionId={} deviceId={}",
          command.commandId(),
          command.submissionId(),
          command.deviceId());
    } catch (MqttException ex) {
      throw new IllegalStateException(
          "mqtt publish failed commandId=" + command.commandId(), ex);
    }
  }

  @Override
  public void disconnected(MqttDisconnectResponse disconnectResponse) {
    log.warn("mqtt disconnected reason={}", disconnectResponse.getReasonString());
  }

  @Override
  public void mqttErrorOccurred(MqttException exception) {
    log.error("mqtt error", exception);
  }

  @Override
  public void messageArrived(String topic, MqttMessage message) {
    String json = new String(message.getPayload(), StandardCharsets.UTF_8);
    try {
      JsonNode node = objectMapper.readTree(json);
      String deviceId = text(node, "deviceId");
      String submissionId = text(node, "submissionId");
      String commandId = text(node, "commandId");
      String status = text(node, "status");
      if (deviceId == null || submissionId == null || commandId == null || status == null) {
        log.error("mqtt status missing required fields topic={}", topic);
        return;
      }
      UUID.fromString(commandId);
      UUID.fromString(submissionId);
      boolean acked = deviceEventStore.markAcknowledged(commandId);
      log.info(
          "mqtt ack commandId={} submissionId={} deviceId={} status={} recorded={}",
          commandId,
          submissionId,
          deviceId,
          status,
          acked);
      heartbeatClient.report(deviceId, submissionId, commandId, status);
    } catch (RuntimeException ex) {
      log.error("mqtt status handling failed topic={}", topic, ex);
    }
  }

  @Override
  public void deliveryComplete(IMqttToken token) {}

  @Override
  public void connectComplete(boolean reconnect, String serverURI) {
    try {
      if (reconnect && client != null) {
        client.subscribe(STATUS_TOPIC, 1).waitForCompletion(completionTimeoutMs);
      }
    } catch (MqttException ex) {
      log.error("mqtt resubscribe failed", ex);
    }
  }

  @Override
  public void authPacketArrived(int reasonCode, MqttProperties properties) {}

  @Override
  public void destroy() throws Exception {
    if (client != null && client.isConnected()) {
      client.disconnect().waitForCompletion(completionTimeoutMs);
    }
    if (client != null) {
      client.close();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
      String text = value.asText();
    return text == null || text.isBlank() ? null : text;
  }

  private record MqttCommandPayload(
      String commandId, String event, int intensity, String submissionId) {}
}
