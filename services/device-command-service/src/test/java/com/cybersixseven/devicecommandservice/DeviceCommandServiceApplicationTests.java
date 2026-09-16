package com.cybersixseven.devicecommandservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class DeviceCommandServiceApplicationTests {

  @Autowired
  private Environment environment;

  @Test
  void contextLoadsWithoutPostgresDatasource() {
    assertFalse(environment.containsProperty("spring.datasource.url"));
    assertEquals("8081", environment.getProperty("server.port"));
  }
}
