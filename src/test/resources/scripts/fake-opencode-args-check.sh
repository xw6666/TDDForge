#!/bin/bash
# Outputs all command-line arguments and stdin prompt for testing command construction
ARGS="$*"
STDIN_PROMPT="$(cat)"
echo "{\"type\":\"text\",\"text\":\"args: ${ARGS}\"}"
echo "{\"type\":\"text\",\"text\":\"stdin: ${STDIN_PROMPT}\"}"
echo '{"type":"step_start","step_start":{"type":"thinking"}}'
echo '{"type":"step_finish","step_finish":{"reason":"stop"}}'
exit 0
