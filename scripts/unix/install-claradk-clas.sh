#!/usr/bin/env bash
#
# Copyright (c) 2021.  Jefferson Science Associates, LLC.
# Subject to the terms in the LICENSE file found in the top-level directory.
# Author gyurjyan
#

# author Vardan Gyurjyan
# date 1.13.17

if ! [ -n "$ERSAP_HOME" ]; then
    echo "ERSAP_HOME environmental variable is not defined. Exiting..."
    exit
fi

rm -rf "$ERSAP_HOME"

mkdir "$ERSAP_HOME"
mkdir "$ERSAP_HOME"/plugins
mkdir "$ERSAP_HOME"/plugins/clas12
mkdir "$ERSAP_HOME"/plugins/clas12/config
mkdir "$ERSAP_HOME"/plugins/clas12/log
mkdir "$ERSAP_HOME"/plugins/clas12/data
mkdir "$ERSAP_HOME"/plugins/clas12/data/input
mkdir "$ERSAP_HOME"/plugins/clas12/data/output

command_exists () {
    type "$1" &> /dev/null ;
}

if ! command_exists git ; then
    echo "Can not run git. Exiting..."
    exit
fi

rm -rf ersap-dk

echo "Creating ersap working directory ..."
mkdir ersap-dk
cd ersap-dk || exit

(
echo "Downloading and building xMsg package ..."
git clone --depth 1 https://github.com/JeffersonLab/xmsg-java.git
cd xmsg-java || exit
./gradlew install
)

(
echo "Downloading and building jinflux package ..."
git clone --depth 1 https://github.com/JeffersonLab/JinFlux.git
cd JinFlux || exit
gradle install
gradle deploy
)

(
echo "Downloading and building ersap-java package ..."
git clone --depth 1 https://github.com/JeffersonLab/ersap-java.git
cd ersap-java || exit
./gradlew install
./gradlew deploy
)

echo "Downloading and building clasrec-io package ..."
git clone --depth 1 https://github.com/JeffersonLab/clasrec-io.git
(
cd clasrec-io || exit
./gradlew deploy
)

echo "Done!"
