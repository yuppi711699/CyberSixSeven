# ESP32 fault matrix

Not run in this session. A serial monitor was already attached to `/dev/cu.usbserial-0001`. Do not start a second monitor. Host-side checks cover backoff, phase changes, NVS blob rejection, ring eviction, and payload rejection (`firmware/esp32/test/test_reliability.cpp`). The rows below need the board.

| Fault | How | Pass |
|---|---|---|
| Wi-Fi loss | Drop the AP or move the board out of range | Reconnects on the 5 s Wi-Fi timer. MQTT is not retried while Wi-Fi is down. No blocking `delay` loop. |
| Broker loss | Stop Mosquitto | Backoff grows 1 s → 30 s. Subscription returns after the broker is back. Effects already running finish. |
| Invalid server trust | Point `MQTT_BROKER_HOST` at a broker whose cert is not signed by the baked-in CA | TLS handshake fails. Firmware does not call `setInsecure()`. |
| Missing client certificate | Flash with empty `CLIENT_CERT` / `CLIENT_KEY` | Serial prints `device certificates missing`. No plaintext MQTT fallback. |
| Duplicate QoS 1 | Publish the same `commandId` twice | Second delivery ACKs and does not repeat the LED / buzzer / motor pattern. |
| Reboot persistence | Handle a command, reboot, deliver the same `commandId` | NVS ring still contains the id. Effect is skipped. ACK is still sent. |

Record the serial lines for each row next to this file before calling the matrix done.
