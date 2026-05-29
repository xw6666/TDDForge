#!/bin/bash
# Outputs content to stdout then exits with non-zero to test stderr capture
echo '{"type":"text","text":"partial output before failure"}'
echo "error: something went wrong" >&2
exit 1
