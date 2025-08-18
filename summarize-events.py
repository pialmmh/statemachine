#!/usr/bin/env python3
import json
import sys
from datetime import datetime

def summarize_events():
    """Generate a chronological summary of events from the event store"""
    
    # Read events from today's file
    events = []
    try:
        with open('event-store/events-2025-08-18.jsonl', 'r') as f:
            content = f.read()
            # Split by actual JSON objects (they start with {" and end with })
            import re
            json_pattern = r'\{"timestamp"[^}]+\}(?:\s*\})?'
            potential_jsons = re.findall(json_pattern, content)
            
            for json_str in potential_jsons:
                try:
                    # Try to parse each potential JSON
                    if not json_str.endswith('}'):
                        json_str += '}'
                    event = json.loads(json_str)
                    events.append(event)
                except:
                    # Try multi-line format
                    lines = content.split('\n')
                    current_json = ''
                    for line in lines:
                        if line.strip():
                            current_json += line
                            if line.strip().endswith('}'):
                                try:
                                    event = json.loads(current_json)
                                    events.append(event)
                                    current_json = ''
                                except:
                                    pass
    except FileNotFoundError:
        print("No events file found for today")
        return
    
    # Sort by timestamp
    events.sort(key=lambda x: x.get('timestamp', ''))
    
    print("Time     | Machine    | Event Summary")
    print("-" * 70)
    
    for e in events:
        # Extract time
        timestamp = e.get('timestamp', '')
        if 'T' in timestamp:
            time = timestamp.split('T')[1][:8]
        else:
            time = 'N/A'
        
        machine = e.get('machineId', 'N/A')[:10]
        category = e.get('eventCategory', 'N/A')
        event_type = e.get('eventType', '')
        
        # Build summary based on category
        if 'REGISTRY_CREATE' in category:
            summary = "Machine created and registered"
        elif 'REGISTRY_REMOVE' in category:
            summary = "Machine removed from registry"
        elif 'REGISTRY_REHYDRATE' in category:
            summary = "Machine rehydrated from offline"
        elif 'WEBSOCKET_IN' in category:
            details = e.get('eventDetails', {})
            if event_type == 'INCOMING_CALL':
                phone = details.get('phoneNumber', 'unknown')
                summary = f"← Received INCOMING_CALL from {phone}"
            elif event_type == 'SESSION_PROGRESS':
                ring = details.get('ringNumber', 0)
                summary = f"← Received SESSION_PROGRESS (ring #{int(ring)})"
            else:
                summary = f"← Received {event_type}"
        elif 'WEBSOCKET_OUT' in category:
            if event_type == 'STATE_CHANGE':
                before = e.get('stateBefore', '?')
                after = e.get('stateAfter', '?')
                summary = f"→ State changed: {before} → {after}"
            elif event_type == 'REGISTRY_CREATE':
                summary = f"→ Broadcast machine creation"
            else:
                summary = f"→ Broadcast {event_type}"
        elif 'STATE_CHANGE' in category:
            before = e.get('stateBefore', '?')
            after = e.get('stateAfter', '?')
            summary = f"State transition: {before} → {after}"
        elif 'TIMEOUT' in category:
            before = e.get('stateBefore', '?')
            after = e.get('stateAfter', '?')
            summary = f"Timeout fired: {before} → {after}"
        else:
            summary = f"{category}: {event_type}"
        
        print(f"{time} | {machine:10} | {summary}")
    
    print("-" * 70)
    print(f"Total events: {len(events)}")

if __name__ == "__main__":
    summarize_events()