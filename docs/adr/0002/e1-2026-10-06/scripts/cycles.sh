#!/bin/bash
# Five interleaved cycles of arms A B C D E on one boot. $1 = boot label (b1, b2). C waits B's measured wait + 2 s.
E=$(dirname "$0")
for c in 1 2 3 4 5; do
  "$E/e1.sh" A "$1-c$c-A"
  "$E/e1.sh" B "$1-c$c-B"
  w=$(grep -oE 'uiautomator wait: [0-9.]+' "$E/runs/$1-c$c-B/steps.txt" | grep -oE '[0-9.]+$')
  "$E/e1.sh" C "$1-c$c-C" "$(perl -e "printf '%.1f', ${w:-6} + 2")"
  "$E/e1.sh" D "$1-c$c-D"
  "$E/e1.sh" E "$1-c$c-E"
  echo "cycle $c done $(date '+%T')"
done
