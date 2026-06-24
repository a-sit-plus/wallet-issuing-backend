#!/bin/bash
set -x
set -e
set -u

#
# This script pulls the artifact from the GitLab repository and deploys
# it to wallet.a-sit.at
#
# Prerequisites:
#   - Needs xq installed if a SNAPSHOT version shall be deployed
#     "pipx install yq"
#     https://github.com/kislyuk/yq
#   - May need a configuration in ~/.ssh/config if your local username
#     does not match the username at wallet.a-sit.at
# 
# Usage:
#   ./deploy.sh "1.0.0-SNAPSHOT" "AAAAAAAAAAAAAAAAAAAA"
#

VERSION=${1:?Please pass version number, e.g. "1.0.0-SNAPSHOT"}
PRIVATE_TOKEN=${2:?Please pass your GitLab access token, e.g. "AAAAAAAAAAAAAAAAAAAA"}

GITLAB_URL="https://gitlab.iaik.tugraz.at/api/v4"
SERVER="wallet.a-sit.at"
CI_PROJECT_ID="750"
PACKAGE_NAME="at/asitplus/wallet"
LIB_NAME="http"
OUTPUT="tmp"

rm -rf "$OUTPUT"
mkdir -p "$OUTPUT"

if [[ "$VERSION" =~ "SNAPSHOT" ]]
then
    EXACT_VERSION=$(curl -sS --fail --header "PRIVATE-TOKEN: $PRIVATE_TOKEN" "$GITLAB_URL/projects/$CI_PROJECT_ID/packages/maven/$PACKAGE_NAME/$LIB_NAME/$VERSION/maven-metadata.xml" | xq ".metadata.versioning.snapshotVersions.snapshotVersion" | jq -r ".[] | select (.extension==\"jar\").value")
    curl -sS --fail --header "PRIVATE-TOKEN: $PRIVATE_TOKEN" "$GITLAB_URL/projects/$CI_PROJECT_ID/packages/maven/$PACKAGE_NAME/$LIB_NAME/$VERSION/http-$EXACT_VERSION.jar" -o "$OUTPUT/$LIB_NAME-$VERSION.jar"
else
    curl -sS --fail --header "PRIVATE-TOKEN: $PRIVATE_TOKEN" "$GITLAB_URL/projects/$CI_PROJECT_ID/packages/maven/$PACKAGE_NAME/$LIB_NAME/$VERSION/http-1.0.0-20220311.104339-1.jar" -o "$OUTPUT/$LIB_NAME-$VERSION.jar"
fi

# From https://stackoverflow.com/a/1885534/1130706
read -p "Press y to deploy $LIB_NAME-$VERSION on $SERVER " -n 1 -r
echo    # (optional) move to a new line
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    [[ "$0" = "$BASH_SOURCE" ]] && exit 1 || return 1 # handle exits from shell or function but don't exit interactive shell
fi


scp "$OUTPUT/$LIB_NAME-$VERSION.jar" "$SERVER:"

ssh -t "$SERVER" bash -c "'
sudo systemctl stop wallet-backend-master
sudo -u wallet cp "~/$LIB_NAME-$VERSION.jar" "/srv/wallet-backend-master/"
sudo -u wallet chmod u+x "/srv/wallet-backend-master/$LIB_NAME-$VERSION.jar"
sudo -u wallet ln -sf "/srv/wallet-backend-master/$LIB_NAME-$VERSION.jar" "/srv/wallet-backend-master/service.jar"
sudo systemctl start wallet-backend-master
'"


