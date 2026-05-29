#!/bin/bash
# Outputs complete NDJSON with sessionId and step_finish stop
cat << 'ENDJSON'
{"type":"text","sessionId":"test-sess","text":"normal output\n"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"text","text":"All good"}
{"type":"step_finish","step_finish":{"reason":"stop"}}
ENDJSON
exit 0
