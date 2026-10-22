#!/bin/bash
# After a conversation is compacted (SessionStart, matcher "compact"), hands the "Start here" section of the Chainsaw
# Man mod's handoff back to Claude, so what's in progress, waiting and decided survives the summary. Reads one file in
# this repo and prints it; nothing else.
f="${CLAUDE_PROJECT_DIR:-.}/chainsawman/HANDOFF.md"
[ -f "$f" ] || exit 0
echo "After compaction: the current state of kortev's Chainsaw Man mod work, from chainsawman/HANDOFF.md (read the whole file before acting on anything not covered here):"
awk '/^## Start here/{p=1} p && /^## / && !/^## Start here/{exit} p' "$f"
