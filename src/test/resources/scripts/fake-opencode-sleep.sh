#!/bin/bash
# Sleeps for a long time to simulate a hanging process (for timeout/kill tests)
# Output some initial content then sleep
echo '{"type":"text","text":"starting long task"}'
sleep 60
