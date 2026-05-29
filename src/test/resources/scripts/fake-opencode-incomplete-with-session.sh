#!/bin/bash
# Outputs incomplete NDJSON with sessionId (no step_finish)
cat << 'ENDJSON'
{"type":"text","sessionId":"test-sess","text":"first run output\n"}
{"type":"step_start","step_start":{"type":"thinking"}}
ENDJSON
exit 0
