# Purpose: makes pytest put `functions` on the import path, so the tests in
# `tests/` can `import main`. It's empty on purpose: the shared fixtures
# live in `tests/conftest.py`.
#
# Outstanding issues: None known.
#
# AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
# (Anthropic), through Claude Code, in November 2026. Aarush Bhat
# reviewed and committed it.
#
# Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
# Sashreek Addanki fix the docstring a merge doubled, on November 1, 2026.
# Sashreek Addanki reviewed it.
"""Empty on purpose: a conftest.py here puts `functions` on the path pytest
collects with, so the tests in `tests/` can `import main`.
"""
