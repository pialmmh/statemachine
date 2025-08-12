#!/usr/bin/env python3
import websocket
import json
import time

# Connect to WebSocket
ws = websocket.WebSocket()
ws.connect("ws://localhost:9998")

# Wait for connection
time.sleep(0.5)

# Send INCOMING_CALL event
incoming_call = {
    "type": "EVENT",
    "eventType": "INCOMING_CALL",
    "payload": {
        "phoneNumber": "+1-555-1234"
    }
}
ws.send(json.dumps(incoming_call))
print("Sent INCOMING_CALL")
time.sleep(1)

# Send SESSION_PROGRESS
session_progress = {
    "type": "EVENT", 
    "eventType": "SESSION_PROGRESS",
    "payload": {
        "sdp": "v=0",
        "ringNumber": 1
    }
}
ws.send(json.dumps(session_progress))
print("Sent SESSION_PROGRESS")
time.sleep(1)

# Send ANSWER
answer = {
    "type": "EVENT",
    "eventType": "ANSWER",
    "payload": {}
}
ws.send(json.dumps(answer))
print("Sent ANSWER")
time.sleep(1)

# Send HANGUP
hangup = {
    "type": "EVENT",
    "eventType": "HANGUP",
    "payload": {}
}
ws.send(json.dumps(hangup))
print("Sent HANGUP")

# Close connection
ws.close()
print("Test completed")