package com.telcobright.statemachine.test;

import com.telcobright.statemachine.StateMachineRegistry;
import com.telcobright.statemachine.GenericStateMachine;
import com.telcobright.statemachineexamples.callmachine.events.IncomingCall;

public class TestHistoryBroadcast {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing History broadcast through debug mode...");
        
        // Connect to existing server and send event
        org.java_websocket.client.WebSocketClient client = new org.java_websocket.client.WebSocketClient(
            new java.net.URI("ws://localhost:9999")) {
            
            @Override
            public void onOpen(org.java_websocket.handshake.ServerHandshake handshake) {
                System.out.println("Connected to WebSocket");
                
                // Send event to trigger state change
                String event = "{\"type\":\"EVENT\",\"machineId\":\"call-002\",\"eventType\":\"INCOMING_CALL\",\"payload\":{\"phoneNumber\":\"+1-555-HIST\"}}";
                send(event);
                System.out.println("Sent INCOMING_CALL event");
            }
            
            @Override
            public void onMessage(String message) {
                // Check for TREEVIEW_STORE_UPDATE
                if (message.contains("TREEVIEW_STORE_UPDATE")) {
                    System.out.println("✓ Received TREEVIEW_STORE_UPDATE!");
                    System.out.println("Store version: " + extractVersion(message));
                    close();
                } else if (message.contains("STATE_CHANGE")) {
                    System.out.println("State change detected");
                }
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Disconnected");
            }
            
            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
            
            private String extractVersion(String json) {
                int idx = json.indexOf("\"version\":");
                if (idx > 0) {
                    int start = idx + 10;
                    int end = json.indexOf(",", start);
                    if (end < 0) end = json.indexOf("}", start);
                    return json.substring(start, end).trim();
                }
                return "unknown";
            }
        };
        
        client.connect();
        Thread.sleep(3000);
        System.out.println("Test completed");
    }
}