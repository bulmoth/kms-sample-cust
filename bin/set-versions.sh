#!/usr/bin/env bash

#/ Version change helper.
#/
#/ This shell script will traverse all subprojects and change their
#/ versions to the one provided as argument.
#/
#/
#/ Notice
#/ ======
#/
#/ This script does not use the Maven `versions` plugin (with goals such as
#/ `versions:update-parent`, `versions:update-child-modules`, or `versions:set`)
#/ because running Maven requires that the *current* versions are all correct
#/ and existing (available for download or installed locally).
#/
#/ We have frequently found that this is a limitation, because some times it is
#/ needed to update from an unexisting version (like if some component is
#/ skipping a patch number, during separate development of different modules),
#/ or when doing a Release (when the release version is not yet available).
#/
#/ It ends up being less troublesome to just edit the pom.xml directly.
#/
#/
#/ Arguments
#/ =========
#/
#/ <BaseVersion>
#/
#/   Base version number to use. When '--release' is used, this string will
#/   be set as-is; otherwise, a nightly/snapshot suffix is added.
#/
#/   <BaseVersion> must be in the Semantic Versioning format, such as "1.2.3"
#/   ("<Major>.<Minor>.<Patch>").
#/
#/ --release
#/
#/   Do not add nightly/snapshot suffix to the base version number.
#/   The resulting value will be valid for a Release build.
#/
#/   Optional. Default: Disabled.
#/
#/ --git-add
#/
#/   Add changes to the Git stage area. Useful to leave everything ready for a
#/   commit.
#/
#/   Optional. Default: Disabled.



# Shell setup
# ===========

# Bash options for strict error checking.
set -o errexit -o errtrace -o pipefail -o nounset

# Check dependencies.
command -v xmlstarlet >/dev/null || {
    echo "ERROR: 'xmlstarlet' is not installed; please install it"
    exit 1
}

# Trace all commands.
set -o xtrace



# Parse call arguments
# ====================

CFG_VERSION=""
CFG_RELEASE="false"
CFG_GIT_ADD="false"

while [[ $# -gt 0 ]]; do
    case "${1-}" in
        --release)
            CFG_RELEASE="true"
            ;;
        --kms-api)
            # Ignore argument.
            if [[ -n "${2-}" ]]; then
                shift
            fi
            ;;
        --git-add)
            CFG_GIT_ADD="true"
            ;;
        *)
            CFG_VERSION="$1"
            ;;
    esac
    shift
done



# Config restrictions
# ===================

if [[ -z "$CFG_VERSION" ]]; then
    echo "ERROR: Missing <Version>"
    exit 1
fi

REGEX='^[[:digit:]]+\.[[:digit:]]+\.[[:digit:]]+$'
[[ "$CFG_VERSION" =~ $REGEX ]] || {
    echo "ERROR: '$CFG_VERSION' is not SemVer (<Major>.<Minor>.<Patch>)"
    exit 1
}

echo "CFG_VERSION=$CFG_VERSION"
echo "CFG_RELEASE=$CFG_RELEASE"
echo "CFG_GIT_ADD=$CFG_GIT_ADD"



# Internal variables
# ==================

if [[ "$CFG_RELEASE" == "true" ]]; then
    VERSION_JAVA="$CFG_VERSION"
else
    VERSION_JAVA="${CFG_VERSION}-SNAPSHOT"
fi



# Helper functions
# ================

# Add the given file(s) to the Git stage area.
function git_add() {
    [[ $# -ge 1 ]] || {
        echo "ERROR [git_add]: Missing argument(s): <file1> [<file2> ...]"
        return 1
    }

    if [[ "$CFG_GIT_ADD" == "true" ]]; then
        git add -- "$@"
    fi
}



# Apply version
# =============

# Parent: Update to the new version of kurento-java.
xmlstarlet edit -S --inplace \
        --update "/_:project/_:parent/_:version" \
        --value "$VERSION_JAVA" \
        pom.xml

# Children: Make them inherit from the new parent.
CHILDREN=(
    kurento-chroma
    kurento-crowddetector
    kurento-group-call
    kurento-hello-world
    kurento-hello-world-recording
    kurento-hello-world-repository
    kurento-magic-mirror
    kurento-metadata-example
    kurento-one2many-call
    kurento-one2one-call
    kurento-one2one-call-advanced
    kurento-one2one-call-recording
    kurento-platedetector
    kurento-player
    kurento-pointerdetector
    kurento-rtp-receiver
    kurento-send-data-channel
    kurento-show-data-channel
)
for CHILD in "${CHILDREN[@]}"; do
    find "$CHILD" -name pom.xml -print0 | xargs -0 -n1 \
        xmlstarlet edit -S --inplace \
            --update "/_:project/_:parent/_:version" \
            --value "$VERSION_JAVA"
done

git_add \
    '*pom.xml'

echo "Done!"
