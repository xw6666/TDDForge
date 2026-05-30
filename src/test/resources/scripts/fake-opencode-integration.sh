#!/bin/bash
# Fake opencode for integration tests.
# Detects agent type from prompt content and outputs appropriate NDJSON.
# Supports SCENARIO env var for override: APPROVE, INVALID, REJECT_CODER, REJECT_TEST, TEST_FAIL, CANCEL

PROMPT="${@: -1}"
SCENARIO="${SCENARIO:-auto}"

if [ "$SCENARIO" = "CANCEL" ]; then
  echo '{"type":"text","sessionId":"int-sess","text":"starting work"}'
  sleep 30
  exit 1
fi

if [ "$SCENARIO" = "TIMEOUT" ]; then
  echo '{"type":"text","sessionId":"int-sess","text":"starting slow work"}'
  sleep 300
  exit 0
fi

case "$PROMPT" in
  *"planning agent"*|*"Analyze the following task"*)
    if [ "$SCENARIO" = "SPLIT" ]; then
      cat << 'EOF'
{"type":"text","sessionId":"int-planner-sess","text":"{\"complexity\":\"complex\",\"split\":true,\"reason\":\"Multiple concerns\",\"sub_tasks\":[{\"title\":\"Sub-task A\",\"description\":\"Implement part A\",\"priority\":\"high\",\"depends_on\":[]},{\"title\":\"Sub-task B\",\"description\":\"Implement part B\",\"priority\":\"medium\",\"depends_on\":[0]}]}"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    else
      cat << 'EOF'
{"type":"text","sessionId":"int-planner-sess","text":"{\"complexity\":\"medium\",\"split\":false,\"reason\":\"Straightforward task\",\"plan\":\"Overall objective: implement the feature\n1. Write tests\n2. Implement code\n3. Review\"}"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    fi
    ;;
  *"test-writing agent"*|*"TDD coding pipeline"*)
    if [ "$SCENARIO" = "INVALID" ]; then
      cat << 'EOF'
{"type":"text","sessionId":"int-tw-sess","text":"## Behavior covered\nTest compilation failed\n## Test command run\n`mvn test`\n## Result classification\nINVALID\n## Commit\ncommitted abc1111111"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    else
      cat << 'EOF'
{"type":"text","sessionId":"int-tw-sess","text":"## Behavior covered\nTest the new feature behavior\n## Test command run\n`mvn test`\n## Result classification\nEXPECTED_RED\n## Commit\ncommitted abc1234567"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    fi
    ;;
  *"test review agent"*|*"tests were written for"*)
    if [ "$SCENARIO" = "REJECT_TEST" ]; then
      cat << 'EOF'
{"type":"text","sessionId":"int-tr-sess","text":"REQUEST_CHANGES\nTests are weak and do not cover edge cases."}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    else
      cat << 'EOF'
{"type":"text","sessionId":"int-tr-sess","text":"APPROVE\nTests look good and cover the expected behavior."}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    fi
    ;;
  *"coding agent"*|*"Implement the following task"*)
    if [ "$SCENARIO" = "TEST_FAIL" ]; then
      cat << 'EOF'
{"type":"text","sessionId":"int-coder-sess","text":"## Implementation summary\nAttempted implementation but tests fail.\n## Test command run\n`mvn test`\n## Test result\n2 tests failed\n## Commit\ncommitted def1111111"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    elif [ "$SCENARIO" = "REJECT_CODER" ]; then
      cat << 'EOF'
{"type":"text","sessionId":"int-coder-sess","text":"## Implementation summary\nFirst implementation attempt.\n## Test command run\n`mvn test`\n## Test result\nAll tests passed\n## Commit\ncommitted def2222222"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    else
      cat << 'EOF'
{"type":"text","sessionId":"int-coder-sess","text":"## Implementation summary\nImplemented the feature as requested.\n## Test command run\n`mvn test`\n## Test result\nAll tests passed\n## Commit\ncommitted def4567890"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    fi
    ;;
  *"code review agent"*|*"was implemented"*)
    if [ "$SCENARIO" = "REJECT_IMPLEMENTATION" ]; then
      cat << 'EOF'
{"type":"text","sessionId":"int-rev-sess","text":"REQUEST_CHANGES\nImplementation has bugs that need fixing."}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    elif [ "$SCENARIO" = "REJECT_TEST_ISSUE" ]; then
      cat << 'EOF'
{"type":"text","sessionId":"int-rev-sess","text":"REQUEST_CHANGES\nTest coverage is insufficient and weak test assertions."}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    else
      cat << 'EOF'
{"type":"text","sessionId":"int-rev-sess","text":"APPROVE\nImplementation looks correct and tests are comprehensive."}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    fi
    ;;
  *"Continue"*)
    cat << 'EOF'
{"type":"text","sessionId":"int-sess","text":"Continuing the work..."}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    ;;
  *)
    cat << 'EOF'
{"type":"text","sessionId":"int-sess","text":"Default output"}
{"type":"step_start","step_start":{"type":"thinking"}}
{"type":"step_finish","step_finish":{"reason":"stop"}}
EOF
    ;;
esac
exit 0
