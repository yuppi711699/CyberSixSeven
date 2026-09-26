package com.cybersixseven.devicecommandservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class DeviceCommandServiceApplicationTests {

  @MockitoBean
  private MqttCommandPublisher mqttCommandPublisher;

  @Autowired
  private Environment environment;

  @Test
  void contextLoadsWithoutPostgresDatasource() {
    assertFalse(environment.containsProperty("spring.datasource.url"));
    assertEquals("0", environment.getProperty("server.port"));
  }

  @Test
  void structuredLogsRedactSecrets(CapturedOutput output) {
    LoggerFactory.getLogger(DeviceCommandServiceApplicationTests.class).info("password=hunter2");
    assertFalse(output.getAll().contains("hunter2"));
    assertTrue(output.getAll().contains("password=[redacted]"));
  }
}
