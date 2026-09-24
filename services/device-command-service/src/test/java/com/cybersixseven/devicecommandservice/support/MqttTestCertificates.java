package com.cybersixseven.devicecommandservice.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class MqttTestCertificates {

  private MqttTestCertificates() {}

  public static Path generate(Path directory) throws Exception {
    Files.createDirectories(directory);
    run(
        directory,
        "openssl",
        "req",
        "-x509",
        "-newkey",
        "rsa:2048",
        "-sha256",
        "-days",
        "2",
        "-nodes",
        "-keyout",
        "ca.key",
        "-out",
        "ca.crt",
        "-subj",
        "/CN=CyberSixSeven Test CA");
    run(
        directory,
        "openssl",
        "req",
        "-newkey",
        "rsa:2048",
        "-nodes",
        "-keyout",
        "broker.rsa.key",
        "-out",
        "broker.csr",
        "-subj",
        "/CN=localhost");
    Files.writeString(
        directory.resolve("broker.ext"),
        "subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n");
    run(
        directory,
        "openssl",
        "x509",
        "-req",
        "-in",
        "broker.csr",
        "-CA",
        "ca.crt",
        "-CAkey",
        "ca.key",
        "-CAcreateserial",
        "-out",
        "broker.crt",
        "-days",
        "2",
        "-sha256",
        "-extfile",
        "broker.ext");
    issueClient(directory, "paho");
    issueClient(directory, "esp32-dev-001");
    run(
        directory,
        "openssl",
        "pkcs8",
        "-topk8",
        "-inform",
        "PEM",
        "-outform",
        "PEM",
        "-nocrypt",
        "-in",
        "broker.rsa.key",
        "-out",
        "broker.key");
    return directory;
  }

  private static void issueClient(Path directory, String name) throws Exception {
    run(
        directory,
        "openssl",
        "req",
        "-newkey",
        "rsa:2048",
        "-nodes",
        "-keyout",
        name + ".rsa.key",
        "-out",
        name + ".csr",
        "-subj",
        "/CN=" + name);
    Files.writeString(
        directory.resolve(name + ".ext"), "extendedKeyUsage=clientAuth\nkeyUsage=digitalSignature\n");
    run(
        directory,
        "openssl",
        "x509",
        "-req",
        "-in",
        name + ".csr",
        "-CA",
        "ca.crt",
        "-CAkey",
        "ca.key",
        "-CAcreateserial",
        "-out",
        name + ".crt",
        "-days",
        "2",
        "-sha256",
        "-extfile",
        name + ".ext");
    run(
        directory,
        "openssl",
        "pkcs8",
        "-topk8",
        "-inform",
        "PEM",
        "-outform",
        "PEM",
        "-nocrypt",
        "-in",
        name + ".rsa.key",
        "-out",
        name + ".key");
  }

  private static void run(Path directory, String... command) throws Exception {
    Process process = new ProcessBuilder(command)
        .directory(directory.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes());
    if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
      throw new IllegalStateException("command failed: " + List.of(command) + "\n" + output);
    }
  }
}
