#!/bin/bash
# Outputs incomplete NDJSON with sessionId, exits with non-zero
cat << 'ENDJSON'
{"type":"text","sessionId":"test-sess","text":"about to fail\n"}
{"type":"step_start","step_start":{"type":"thinking"}}
ENDJSON
exit 1
