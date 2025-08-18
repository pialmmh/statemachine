#!/usr/bin/env python3
import json
import sys
from datetime import datetime
from pathlib import Path

def format_event(event_json):
    """Format a single event for display"""
    try:
        # Parse the timestamp
        timestamp = event_json.get('timestamp', 'N/A')
        if timestamp != 'N/A':
            try:
                # Parse ISO format timestamp
                dt = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
                timestamp = dt.strftime('%H:%M:%S')
            except:
                pass
        
        # Build formatted output
        output = []
        output.append(f"\n{'='*60}")
        output.append(f"🕐 Time: {timestamp}")
        output.append(f"📁 Category: {event_json.get('eventCategory', 'N/A')}")
        output.append(f"📋 Type: {event_json.get('eventType', 'N/A')}")
        
        machine_id = event_json.get('machineId')
        if machine_id:
            output.append(f"🤖 Machine: {machine_id}")
        
        # Show state transition if present
        state_before = event_json.get('stateBefore')
        state_after = event_json.get('stateAfter')
        if state_before and state_after:
            output.append(f"🔄 Transition: {state_before} → {state_after}")
        
        # Show source and destination
        source = event_json.get('source')
        dest = event_json.get('destination')
        if source and dest:
            output.append(f"📍 Flow: {source} → {dest}")
        
        # Show success/error
        success = event_json.get('success', True)
        if not success:
            output.append(f"❌ Error: {event_json.get('errorMessage', 'Unknown error')}")
        else:
            output.append("✅ Success")
        
        # Show processing time if significant
        processing_time = event_json.get('processingTimeMs', 0)
        if processing_time > 0:
            output.append(f"⏱️ Processing: {processing_time}ms")
        
        # Show event details if present
        details = event_json.get('eventDetails')
        if details:
            output.append(f"📝 Details: {json.dumps(details, indent=2)}")
        
        return '\n'.join(output)
    except Exception as e:
        return f"Error formatting event: {e}"

def read_events_file(filepath):
    """Read events from a JSON lines file"""
    events = []
    current_json = []
    
    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            if line:
                current_json.append(line)
                # Check if we have a complete JSON object
                if line == '}':
                    try:
                        json_str = '\n'.join(current_json)
                        event = json.loads(json_str)
                        events.append(event)
                        current_json = []
                    except json.JSONDecodeError:
                        # Not complete yet, continue
                        pass
    
    return events

def main():
    # Default to today's events
    today = datetime.now().strftime('%Y-%m-%d')
    
    # Check command line arguments
    if len(sys.argv) > 1:
        if sys.argv[1] == '--help':
            print("Usage: python3 view-events.py [date] [--filter category|machine]")
            print("Examples:")
            print("  python3 view-events.py                    # Today's events")
            print("  python3 view-events.py 2025-08-17         # Specific date")
            print("  python3 view-events.py --filter STATE_CHANGE")
            print("  python3 view-events.py --filter call-001")
            print("  python3 view-events.py --last 10          # Last 10 events")
            print("  python3 view-events.py --stats            # Statistics")
            return
        elif sys.argv[1] == '--stats':
            # Show statistics
            event_store = Path('event-store')
            total_events = 0
            total_size = 0
            files = []
            
            for filepath in event_store.glob('events-*.jsonl'):
                files.append(filepath.name)
                events = read_events_file(filepath)
                total_events += len(events)
                total_size += filepath.stat().st_size
            
            print("\n📊 EventStore Statistics")
            print("="*40)
            print(f"📁 Location: ./event-store/")
            print(f"📅 Files: {len(files)}")
            for f in sorted(files):
                print(f"   - {f}")
            print(f"📈 Total Events: {total_events}")
            print(f"💾 Total Size: {total_size / 1024:.1f} KB")
            print(f"🔄 Retention: 7 days")
            print(f"✅ Status: Active")
            return
        elif '--' not in sys.argv[1]:
            today = sys.argv[1]
    
    # Build file path
    filepath = Path(f'event-store/events-{today}.jsonl')
    
    if not filepath.exists():
        print(f"❌ No events file found for {today}")
        print("Available files:")
        for f in Path('event-store').glob('events-*.jsonl'):
            print(f"  - {f.name}")
        return
    
    # Read events
    events = read_events_file(filepath)
    
    # Apply filters
    if '--filter' in sys.argv:
        filter_idx = sys.argv.index('--filter')
        if filter_idx + 1 < len(sys.argv):
            filter_val = sys.argv[filter_idx + 1]
            filtered = []
            for event in events:
                # Check if filter matches category, type, or machine ID
                if (filter_val == event.get('eventCategory') or 
                    filter_val == event.get('eventType') or
                    filter_val == event.get('machineId')):
                    filtered.append(event)
            events = filtered
            print(f"🔍 Filtered to {len(events)} events matching '{filter_val}'")
    
    # Handle --last option
    if '--last' in sys.argv:
        last_idx = sys.argv.index('--last')
        if last_idx + 1 < len(sys.argv):
            last_n = int(sys.argv[last_idx + 1])
            events = events[-last_n:]
            print(f"📌 Showing last {last_n} events")
    
    # Display events
    print(f"\n📅 Events for {today}")
    print(f"📊 Total: {len(events)} events")
    
    for event in events:
        print(format_event(event))
    
    print(f"\n{'='*60}")
    print(f"Total events displayed: {len(events)}")

if __name__ == '__main__':
    main()