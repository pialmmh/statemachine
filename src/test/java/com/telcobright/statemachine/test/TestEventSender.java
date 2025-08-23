package com.telcobright.statemachine.test;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

public class TestEventSender {
    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://localhost:9999");
        
        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Connected to WebSocket server");
                
                // Send test event
                String event = "{\"type\":\"EVENT\",\"machineId\":\"call-002\",\"eventType\":\"INCOMING_CALL\",\"payload\":{\"phoneNumber\":\"+1-555-TEST\"}}";
                System.out.println("Sending event: " + event);
                send(event);
                
                // Close after a short delay
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
            
            @Override
            public void onMessage(String message) {
                System.out.println("Received: " + message);
                if (message.contains("EVENT_FIRED")) {
                    System.out.println("✓ Event successfully fired!");
                } else if (message.contains("EVENT_ERROR")) {
                    System.out.println("✗ Event error occurred");
                }
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Connection closed");
            }
            
            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };
        
        client.connect();
        Thread.sleep(2000);
        System.out.println("Test completed");
    }
}