#!/bin/sh
exec node "$(dirname "$0")/../skills/optel-analyze-errors/scripts/improved-error-similarity.jsh" "$@"
