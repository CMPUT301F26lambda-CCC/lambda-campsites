# AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
# this file in October 2026, starting from the file `firebase init` creates.
# It helped with the comments and the Python syntax. Sashreek Addanki reviewed
# it and committed it.
"""Cloud Functions for Lambda Campsite.

docs/architecture.md section 4 lists every function: drawLotteries,
sendPush, updateRatingStats, cleanUpCampsite and cleanUpProfile. Each one
gets added here by its own issue.
"""

from firebase_admin import initialize_app
from firebase_functions.options import set_global_options

# At most 10 copies of each function run at once, so a bug or a burst of
# requests can't start hundreds of them and run up a bill.
set_global_options(max_instances=10)

initialize_app()
