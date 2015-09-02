#!/bin/bash

SERVER="http://scrollback.io"
WWW="src/main/assets/www"

if [[ $1 ]]; then
    SERVER="$1"
fi

echo "Server set to $SERVER"

# Remove old files
rm -rf $WWW
mkdir -p $WWW

# Get index.html file
wget "$SERVER/index.html" -O "$WWW/index.html"

# Get the manifest file
wget "$SERVER/manifest.appcache" -O "$WWW/manifest.appcache"

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

        wget "$SERVER/$line" -O "$WWW/$line"
    fi

done < "$WWW/manifest.appcache"
