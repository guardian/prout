#!/bin/bash

# =============================================================================
# Reports to Prout the result of post-deployment test executed by Travis build.
#
# Sends POST request to Prout's Tavis webhook.
# Specified to be called by after_script field in .prout.json
#
# References:
#   - http://docs.travis-ci.com/user/customizing-the-build/
#   - http://docs.travis-ci.com/user/environment-variables/
# =============================================================================
echo "Feeding test results back to Prout..."

# Exit as soon as one command returns a non-zero exit code.
# Display expanded commands
set -ex

# Prout's Travis webhook
PROUT_HOOK=https://my-prout-host/api/hooks/travis

# SauceLabs session ID
if [ ! -f ./logs/screencastId ]; then
    SCREENCAST_ID=""
else
    SCREENCAST_ID=`cat ./logs/screencastId`
fi

# Json representing testing-in-production results
TEST_RESULT="{\"repoSlug\":\"$TRAVIS_REPO_SLUG\",\"commit\":\"$TRAVIS_COMMIT\",\"testResult\":\"$TRAVIS_TEST_RESULT\",\"buildId\":\"$TRAVIS_BUILD_ID\",\"screencastId\":\"$SCREENCAST_ID\"}"

# POST test results to Prout
curl -X POST -H "Content-Type: application/json" -d $TEST_RESULT $PROUT_HOOK
