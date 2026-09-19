package fr.vanillainstincts.legacy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Tiny loopback-only HTTP proxy used by ForgeGradle 2.0.
 *
 * ForgeGradle 2.0 hardcodes http://export.mcpbot.bspk.rs/versions.json.
 * MCPBot is gone, so the shell wrapper downloads a compatible archived index
 * from a maintained HTTPS mirror and this process serves that one file locally.
 */
public final class McpbotIndexProxy {
    private static final String ETAG = "\"vanilla-instincts-mcpbot-index-v1\"";

    private McpbotIndexProxy() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: McpbotIndexProxy <versions.json> <port-file>");
            System.exit(2);
        }

        final File jsonFile = new File(args[0]);
        final File portFile = new File(args[1]);
        final byte[] json = readAll(jsonFile);
        if (json.length == 0) {
            throw new IOException("Empty MCP versions index: " + jsonFile);
        }

        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                Headers request = exchange.getRequestHeaders();
                String host = request.getFirst("Host");
                if (host != null) {
                    int colon = host.indexOf(':');
                    if (colon >= 0) host = host.substring(0, colon);
                }

                // Only impersonate the one dead MCPBot hostname. Any accidental
                // proxy traffic fails closed instead of receiving JSON nonsense.
                if (host != null && !"export.mcpbot.bspk.rs".equalsIgnoreCase(host)) {
                    byte[] message = "Vanilla Instincts MCPBot proxy: unsupported host\n"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(502, message.length);
                    OutputStream out = exchange.getResponseBody();
                    out.write(message);
                    out.close();
                    return;
                }

                Headers response = exchange.getResponseHeaders();
                response.set("Content-Type", "application/json; charset=utf-8");
                response.set("Cache-Control", "no-cache");
                response.set("ETag", ETAG);

                String incomingEtag = request.getFirst("If-None-Match");
                if (ETAG.equals(incomingEtag)) {
                    exchange.sendResponseHeaders(304, -1);
                    exchange.close();
                    return;
                }

                if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                    response.set("Content-Length", Integer.toString(json.length));
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                    return;
                }

                exchange.sendResponseHeaders(200, json.length);
                OutputStream out = exchange.getResponseBody();
                out.write(json);
                out.close();
            }
        });
        server.setExecutor(null);
        server.start();

        File parent = portFile.getParentFile();
        if (parent != null) parent.mkdirs();
        FileWriter writer = new FileWriter(portFile, false);
        try {
            writer.write(Integer.toString(server.getAddress().getPort()));
            writer.write("\n");
        } finally {
            writer.close();
        }

        // HttpServer owns non-daemon worker threads and keeps the JVM alive.
        // The shell wrapper terminates this process when Gradle exits.
    }

    private static byte[] readAll(File file) throws IOException {
        long length = file.length();
        if (length <= 0L || length > Integer.MAX_VALUE) {
            throw new IOException("Invalid MCP versions index size: " + length);
        }
        byte[] data = new byte[(int) length];
        FileInputStream in = new FileInputStream(file);
        try {
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != data.length) {
                throw new IOException("Short read for MCP versions index");
            }
            return data;
        } finally {
            in.close();
        }
    }
}
