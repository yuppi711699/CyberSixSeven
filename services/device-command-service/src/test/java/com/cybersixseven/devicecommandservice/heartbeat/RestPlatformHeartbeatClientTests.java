package com.cybersixseven.devicecommandservice.heartbeat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestPlatformHeartbeatClientTests {

  private HttpServer server;
  private String baseUrl;
  private final AtomicReference<String> method = new AtomicReference<>();
  private final AtomicReference<String> path = new AtomicReference<>();
  private final AtomicReference<String> apiKey = new AtomicReference<>();
  private final AtomicReference<String> body = new AtomicReference<>();
  private final CountDownLatch latch = new CountDownLatch(1);

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/devices/esp32-dev-001/heartbeat", exchange -> {
      method.set(exchange.getRequestMethod());
      path.set(exchange.getRequestURI().getPath());
      apiKey.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
      latch.countDown();
    });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void reportSendsAuthenticatedPatchWithCommandId() throws Exception {
    RestPlatformHeartbeatClient client =
        new RestPlatformHeartbeatClient(baseUrl, "test-internal-api-key", 500, 1000);

    client.report(
        "esp32-dev-001",
        "22222222-2222-2222-2222-222222222222",
        "11111111-1111-1111-1111-111111111111",
        "handled");

    assertTrue(latch.await(2, TimeUnit.SECONDS));
    assertEquals("PATCH", method.get());
    assertEquals("/internal/devices/esp32-dev-001/heartbeat", path.get());
    assertEquals("test-internal-api-key", apiKey.get());
    assertTrue(body.get().contains("11111111-1111-1111-1111-111111111111"));
    assertTrue(body.get().contains("22222222-2222-2222-2222-222222222222"));
    assertTrue(body.get().contains("handled"));
  }

  @Test
  void reportDoesNotThrowWhenTheApiIsDown() {
    RestPlatformHeartbeatClient client =
        new RestPlatformHeartbeatClient("http://127.0.0.1:1", "test-internal-api-key", 100, 100);
    client.report("esp32-dev-001", "s", "c", "handled");
  }
}
