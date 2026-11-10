#!/bin/bash
# After a conversation is compacted (SessionStart, matcher "compact"), hands Claude the Chainsaw Man mod's one-page
# status, so what's running, waiting and decided survives the summary. It used to print the whole "Start here"
# section of HANDOFF.md (104 KB every time); kortev asked for that to be cut on 2026-11-09. Reads one file in this
# repo and prints it; nothing else.
f="${CLAUDE_PROJECT_DIR:-.}/chainsawman/STATUS.md"
[ -f "$f" ] || exit 0
echo "After compaction: the current state of kortev's Chainsaw Man mod work, from chainsawman/STATUS.md. Do not read chainsawman/HANDOFF.md whole (153 KB); search it for what you need."
cat "$f"
