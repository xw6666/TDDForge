#!/bin/bash
# Fake opencode CLI for testing
# Detects "Continue" prompt to simulate auto-continue behavior.
# For simple static modes, use the dedicated scripts below.

PROMPT="${@: -1}"

if [[ "$PROMPT" == "Continue" ]]; then
  cat << 'ENDJSON'
{"type":"text","sessionId":"test-sess","text":"continuation output\n"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"text","text":"Continuing the work..."}
{"type":"step_finish","step_finish":{"reason":"stop"}}
ENDJSON
  exit 0
fi

cat << 'ENDJSON'
{"type":"text","sessionId":"test-sess","text":"first run output\n"}
{"type":"step_start","step_start":{"type":"thinking"}}
ENDJSON
exit 0
