#!/usr/bin/env python3
"""
Test script to validate the rehydration flow for offline machines.
This script will:
1. Connect to WebSocket server
2. Send INCOMING_CALL to call-003 (which should be online)
3. Wait for it to timeout to offline
4. Send another event to trigger rehydration
"""

import asyncio
import websockets
import json
import time

async def test_rehydration():
    uri = "ws://localhost:9999"
    
    async with websockets.connect(uri) as websocket:
        print("Connected to WebSocket server")
        
        # Send INCOMING_CALL to call-003
        event1 = {
            "type": "sendEvent",
            "machineId": "call-003",
            "eventType": "INCOMING_CALL",
            "payload": {"caller": "+1234567890", "timestamp": str(time.time())}
        }
        
        print("\n1. Sending INCOMING_CALL to call-003...")
        await websocket.send(json.dumps(event1))
        
        # Wait for response
        response = await websocket.recv()
        print(f"Response: {response}")
        
        # Send ANSWER event
        event2 = {
            "type": "sendEvent",
            "machineId": "call-003",
            "eventType": "ANSWER",
            "payload": {"answeredBy": "agent-001"}
        }
        
        print("\n2. Sending ANSWER to call-003...")
        await websocket.send(json.dumps(event2))
        
        response = await websocket.recv()
        print(f"Response: {response}")
        
        print("\n3. Waiting for timeout (35 seconds)...")
        await asyncio.sleep(35)
        
        # Now machine should be offline - send event to trigger rehydration
        event3 = {
            "type": "sendEvent",
            "machineId": "call-003",
            "eventType": "INCOMING_CALL",
            "payload": {"caller": "+9876543210", "timestamp": str(time.time())}
        }
        
        print("\n4. Sending INCOMING_CALL to offline call-003 (should trigger rehydration)...")
        await websocket.send(json.dumps(event3))
        
        response = await websocket.recv()
        print(f"Response: {response}")
        
        print("\n5. Test completed - check backend logs for rehydration behavior")

if __name__ == "__main__":
    asyncio.run(test_rehydration())