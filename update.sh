#!/bin/bash

downloadFile() {
    echo "Downloading $1 to $(pwd)/$2"
    curl -C - -L -k -# $1 > $2

    [[ $? == 0 ]] || exit 1
}

getVariable() {
    echo $(grep "$1" "$2" | tr " " "\n" | tail -n 1 | sed 's/[";]//g')
}

DIRNAME="$1"
PACKAGE="$2"
FILENAME="$3"

if [[ "$DIRNAME" != "" && "$PACKAGE" != "" && "$FILENAME" != "" ]]; then
    CONSTANTS="$DIRNAME/java/$(echo $PACKAGE | sed 's/\./\//g')/$FILENAME"

    PROTOCOL=$(getVariable "PROTOCOL" "$CONSTANTS")
    HOST=$(getVariable "HOST" "$CONSTANTS")
    INDEX=$(getVariable "PATH" "$CONSTANTS")
else
    echo "Invalid parameters passed"

    exit 1
fi

SERVER="$PROTOCOL//$HOST"
WWW="$DIRNAME/assets/www"

echo "Server set to $SERVER"

# Remove old files
rm -rf $WWW
mkdir -p $WWW

# Get index file
downloadFile "$SERVER/$INDEX" "$WWW/$INDEX"

# Get the manifest file
downloadFile "$SERVER/manifest.appcache" "$WWW/manifest.appcache"

# Read the manifest appcache
ISCACHE=false

while IFS='' read -r line || [[ -n "$line" ]]; do
    if [[ $line =~ [A-Z]+: ]]; then
        if [[ $line == "CACHE:" ]]; then
            ISCACHE=true
        else
            if [[ $ISCACHE == true ]]; then
                ISCACHE=false

                break
            fi
        fi

        continue
    fi

    if [[ $ISCACHE == true && $line ]]; then
        mkdir -p "$WWW/${line%/*}"

        downloadFile "$SERVER/$line" "$WWW/$line"
    fi

done < "$WWW/manifest.appcache"
