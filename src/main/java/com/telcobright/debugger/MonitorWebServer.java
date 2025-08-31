package com.telcobright.debugger;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Simple HTTP server to serve the monitoring UI
 */
public class MonitorWebServer {
    
    private static final int DEFAULT_PORT = 8080;
    private HttpServer server;
    
    public MonitorWebServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null); // creates a default executor
    }
    
    public void start() {
        server.start();
        System.out.println("Monitor Web Server started on http://localhost:" + server.getAddress().getPort());
        System.out.println("Open your browser and navigate to http://localhost:" + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Default to xstate-snapshot-viewer.html
            if (path.equals("/") || path.equals("/index.html")) {
                path = "/xstate-snapshot-viewer.html";
            }
            
            // Try to load from classpath resources
            InputStream resourceStream = getClass().getResourceAsStream(path);
            
            if (resourceStream != null) {
                // Determine content type
                String contentType = "text/html";
                if (path.endsWith(".css")) {
                    contentType = "text/css";
                } else if (path.endsWith(".js")) {
                    contentType = "application/javascript";
                } else if (path.endsWith(".json")) {
                    contentType = "application/json";
                }
                
                byte[] response = resourceStream.readAllBytes();
                
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, response.length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
                resourceStream.close();
            } else {
                // 404 Not Found
                String response = "404 - File not found: " + path;
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
    
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }
        
        try {
            MonitorWebServer webServer = new MonitorWebServer(port);
            webServer.start();
            
            // Keep server running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down web server...");
                webServer.stop();
            }));
            
            // Keep the main thread alive
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}