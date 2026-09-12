package com.docshub.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Resolves the address of the collaboration server, so it is not compiled into the client.
 *
 * First match wins:
 * <ol>
 *   <li>{@code -Ddocshub.server=host:port} on the JVM command line</li>
 *   <li>a {@code DOCSHUB_SERVER} environment variable</li>
 *   <li>{@code DOCSHUB_SERVER} in a {@code .env} file in the working directory</li>
 *   <li>{@code localhost:8080}</li>
 * </ol>
 *
 * The value is a host and port; the {@code ws://} and {@code http://} URLs are derived from it,
 * which keeps one setting for both. A TLS deployment would need {@code wss://} / {@code https://}
 * here instead.
 */
public final class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    private static final String ENV_KEY = "DOCSHUB_SERVER";
    private static final String SYSTEM_PROPERTY = "docshub.server";
    private static final String ENV_FILE = ".env";
    private static final String DEFAULT_SERVER = "localhost:8080";

    private static final String SERVER = resolveServer();

    private Config() {
    }

    /**
     * @return the STOMP/WebSocket endpoint, e.g. "ws://localhost:8080/ws"
     */
    public static String webSocketUrl() {
        return "ws://" + SERVER + "/ws";
    }

    /**
     * @return the base URL of the document REST API, e.g. "http://localhost:8080/document/"
     */
    public static String documentApiUrl() {
        return "http://" + SERVER + "/document/";
    }

    /**
     * @return the configured host and port, e.g. "localhost:8080"
     */
    public static String server() {
        return SERVER;
    }

    private static String resolveServer() {
        String fromSystemProperty = System.getProperty(SYSTEM_PROPERTY);
        if (isSet(fromSystemProperty)) {
            return announce(fromSystemProperty, "-D" + SYSTEM_PROPERTY);
        }

        String fromEnvironment = System.getenv(ENV_KEY);
        if (isSet(fromEnvironment)) {
            return announce(fromEnvironment, "environment variable " + ENV_KEY);
        }

        String fromEnvFile = readEnvFile();
        if (isSet(fromEnvFile)) {
            return announce(fromEnvFile, ENV_FILE);
        }

        return announce(DEFAULT_SERVER, "built-in default");
    }

    /**
     * Reads DOCSHUB_SERVER from a .env file in the working directory, if one exists.
     * The .env format (KEY=VALUE lines, # for comments) is a subset of what
     * {@link Properties} already parses, so this needs no extra dependency.
     *
     * @return the configured value, or null if there is no readable .env or no such key
     */
    private static String readEnvFile() {
        Path envFile = Path.of(ENV_FILE);
        if (!Files.isReadable(envFile)) {
            return null;
        }

        Properties properties = new Properties();
        try (InputStream contents = Files.newInputStream(envFile)) {
            properties.load(contents);
        } catch (IOException e) {
            log.warn("Could not read {}, falling back to the next source", envFile.toAbsolutePath(), e);
            return null;
        }

        return properties.getProperty(ENV_KEY);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String announce(String server, String source) {
        String trimmed = server.trim();
        log.info("Server address {} (from {})", trimmed, source);
        return trimmed;
    }
}
