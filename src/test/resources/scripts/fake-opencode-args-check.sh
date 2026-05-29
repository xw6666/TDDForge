#!/bin/bash
# Outputs all command-line arguments as NDJSON for testing command construction
ARGS="$*"
echo "{\"type\":\"text\",\"text\":\"args: ${ARGS}\"}"
echo '{"type":"step_start","step_start":{"type":"thinking"}}'
echo '{"type":"step_finish","step_finish":{"reason":"stop"}}'
exit 0
