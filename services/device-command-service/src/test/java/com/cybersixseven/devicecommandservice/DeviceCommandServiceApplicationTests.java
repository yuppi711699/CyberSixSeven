package com.cybersixseven.devicecommandservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
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
}
