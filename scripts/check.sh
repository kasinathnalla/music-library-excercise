#!/usr/bin/env sh
# Everything a change has to survive. Fails on the first problem.
#
# Standards first, deliberately: it needs no Docker and no network, runs in under a second, and is
# therefore the one check that still works when the test suites cannot. Every rule in it has
# already caused a real bug in this repository at least once.
#
# The backend suite needs a working Docker daemon — tests run against a real PostgreSQL through
# Testcontainers. Without one this script fails rather than skipping, because a suite that did not
# run is not a suite that passed.
#
# yarn, not npm — npm cannot install this dependency tree.
set -eu

echo "==> standards (AGENTS.md conventions, mechanically enforced)"
python3 scripts/standards-check.py

echo "==> backend: gradle test"
(cd backend && ./gradlew test)

echo "==> frontend: vitest"
(cd frontend && yarn test --run)

echo "==> frontend: production build"
(cd frontend && yarn build)

echo "==> all checks passed"
