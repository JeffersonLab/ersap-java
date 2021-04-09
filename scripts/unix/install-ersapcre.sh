#!/usr/bin/env bash
#
# Copyright (c) 2021.  Jefferson Science Associates, LLC.
# Subject to the terms in the LICENSE file found in the top-level directory.
# Author gyurjyan
#

# author Vardan Gyurjyan
# date 1.10.19

is_local="false"

if ! [ -n "$ERSAP_HOME" ]; then
    echo "ERSAP_HOME environmental variable is not defined. Exiting..."
    exit
fi

echo "If you have an old installation at $ERSAP_HOME it will be deleted."
read -n 1 -p "Do you want to continue? Y/N `echo $'\n> '`" uinput
if [ "$uinput" != "Y" ]; then exit 0
fi

rm -rf "$ERSAP_HOME"

FV=4.3.5

case "$1" in
    -f | --framework)
        if ! [ -z "${2+x}" ]; then FV=$2; fi
        echo "ERSAP version = $FV"
        ;;
esac

command_exists () {
    type "$1" &> /dev/null ;
}

OS=$(uname)
case $OS in
    'Linux')

        if ! command_exists wget ; then
            echo "Can not run wget. Exiting..."
            exit
        fi

        wget https://userweb.jlab.org/~gurjyan/ersap-cre/ersap-cre-$FV.tar.gz

        MACHINE_TYPE=$(uname -m)
        if [ "$MACHINE_TYPE" == "x86_64" ]; then
            wget https://userweb.jlab.org/~gurjyan/ersap-cre/linux-64.tar.gz
        else
            wget https://userweb.jlab.org/~gurjyan/ersap-cre/linux-i586.tar.gz
        fi
        ;;

    #  'WindowsNT')
        #    OS='Windows'
        #    ;;

    'Darwin')

        if ! command_exists curl ; then
            echo "Can not run curl. Exiting..."
            exit
        fi

        curl "https://userweb.jlab.org/~gurjyan/ersap-cre/ersap-cre-$FV.tar.gz" -o ersap-cre-$FV.tar.gz

        curl "https://userweb.jlab.org/~gurjyan/ersap-cre/macosx-64.tar.gz" -o macosx-64.tar.gz
        ;;

    *) ;;
esac

tar xvzf ersap-cre-$FV.tar.gz
rm -f ersap-cre-$FV.tar.gz

(
mkdir ersap-cre/jre
cd ersap-cre/jre || exit

mv ../../*.tar.* .
echo "Installing jre ..."
tar xvzf ./*.tar.*
rm -f ./*.tar.*
)

mv ersap-cre "$ERSAP_HOME"

chmod a+x "$ERSAP_HOME"/bin/*

rm -rf $ERSAP_HOME/plugins/clas12

echo "Distribution  :    ersap-cre-$FV" > "$ERSAP_HOME"/.version
echo "Done!"
