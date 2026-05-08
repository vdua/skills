#!/bin/sh
exec node "$(dirname "$0")/../skills/optel-query/scripts/optel-query.jsh" "$@"
