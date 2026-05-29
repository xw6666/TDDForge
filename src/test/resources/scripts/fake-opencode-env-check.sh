#!/bin/bash
# Outputs the OPENCODE_CONFIG env var as NDJSON for testing env injection
echo "{\"type\":\"text\",\"text\":\"OPENCODE_CONFIG=${OPENCODE_CONFIG}\"}"
echo '{"type":"step_start","step_start":{"type":"thinking"}}'
echo '{"type":"step_finish","step_finish":{"reason":"stop"}}'
exit 0
