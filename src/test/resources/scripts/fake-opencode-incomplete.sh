#!/bin/bash
# Outputs incomplete NDJSON (no step_finish, no sessionId)
cat << 'ENDJSON'
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"text","text":"Working on something..."}
ENDJSON
exit 0
