#!/bin/bash
#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# Simple tool to diff two shared libraries. 
# Criterias: two shared libraries are considered equal if:
# the file sizes are the same AND the symbols outputs from the nm command are equal

if [ $# -lt 2 ] 
then
  echo "Diff two shared libs. Return codes:"
  echo "0 - no diff"
  echo "1 - Identical symbols AND size, BUT not bytewise identical"
  echo "2 - Identical symbols BUT NEW size"
  echo "3 - Differences, content BUT SAME size"
  echo "4 - Differences, content AND size"
  echo "10 - Could not perform diff"
  echo "Use 'quiet' to disable any output."
  echo "Syntax: $0 file1 file2 [quiet]"
  exit 10
fi

if [ ! -f $1 ]
then
  echo $1 does not exist
  exit 10
fi

if [ ! -f $2 ]
then
  echo $2 does not exist
  exit 10
fi

if [ "`uname`" == "SunOS" ]; then
    if [ -f "`which gnm`" ]; then
        NM=gnm
# Jonas 2012-05-29: solaris native nm produces radically different output than gnm
#                   so if using that...we need different filter than "cut -f 2-"
#
    elif [ -f "`which nm`" ]; then
        NM=nm
    else
        echo "No nm command found"
        exit 10
    fi
    LDD=ldd
elif [ $OSTYPE == "cygwin" ]; then
    NM="$VS100COMNTOOLS/../../VC/bin/amd64/dumpbin.exe"
    NM_ARGS=/exports
    LDD=
elif [ "`uname`" == "Darwin" ]; then
    NM=nm
    LDD="otool -L"
else
    NM=nm
    LDD=ldd
fi

# Should the differences be viewed?
VIEW=
# You can do export DIFF=meld to view
# any differences using meld instead.
if [ -n "$DIFF" ]; then
    DIFF="$DIFF"
else
    DIFF=diff
fi
OLD=$(cd $(dirname $1) && pwd)/$(basename $1)
NEW=$(cd $(dirname $2) && pwd)/$(basename $2)

OLD_SIZE=$(ls -l "$OLD" | awk '{ print $5 }')
NEW_SIZE=$(ls -l "$NEW" | awk '{ print $5 }')

if [ $# -gt 3 ]
then
    ROOT1=$(cd $3 && pwd)
    ROOT2=$(cd $4 && pwd)
    OLD_NAME=$(echo $OLD | sed "s|$ROOT1/||")
    NEW_NAME=$(echo $NEW | sed "s|$ROOT2/||")
    if [ "x$5" == "xview" ]; then VIEW=view; fi
else
    ROOT1=$(dirname $OLD)/
    ROOT2=$(dirname $NEW)/
    OLD_NAME=$OLD
    NEW_NAME=$NEW
    if [ "x$3" == "xview" ]; then VIEW=view; fi
fi

OLD_SUFFIX="${OLD##*.}"
NEW_SUFFIX="${NEW##*.}"
if [ "$OLD_SUFFIX" != "$NEW_SUFFIX" ]; then
    echo The files do not have the same suffix type!
    exit 10
fi

if [ "$OLD_SUFFIX" != "so" ] && [ "$OLD_SUFFIX" != "dylib" ] && [ "$OLD_SUFFIX" != "dll" ]; then
    echo The files have to be .so, .dylib or .dll! They are $OLD_SUFFIX
    exit 10
fi

TYPE="$OLD_SUFFIX"

if cmp $OLD $NEW > /dev/null
then
    # The files were bytewise identical.
    echo Identical: $OLD_NAME
    exit 0
fi

OLD_SYMBOLS=$COMPARE_ROOT/nm.$OLD_NAME.old
NEW_SYMBOLS=$COMPARE_ROOT/nm.$NEW_NAME.new

mkdir -p $(dirname $OLD_SYMBOLS)
mkdir -p $(dirname $NEW_SYMBOLS)

if [ $OSTYPE == "cygwin" ]; then
    "$NM" $NM_ARGS $OLD | grep " = " > $OLD_SYMBOLS
    "$NM" $NM_ARGS $NEW | grep " = " > $NEW_SYMBOLS
    "$NM" $NM_ARGS $OLD > $OLD_SYMBOLS.full
    "$NM" $NM_ARGS $NEW > $NEW_SYMBOLS.full
else
    # Strip the addresses, just compare the ordering of the symbols.
    $NM $OLD | cut -f 2- -d ' ' > $OLD_SYMBOLS
    $NM $NEW | cut -f 2- -d ' ' > $NEW_SYMBOLS
    # But store the full information for easy diff access.
    $NM $OLD  > $OLD_SYMBOLS.full
    $NM $NEW  > $NEW_SYMBOLS.full
fi

DIFFS=$(LANG=C diff $OLD_SYMBOLS $NEW_SYMBOLS)

RESULT=0

if [ "${LDD}" ]
then
    NAME=`basename $OLD`
    TMP=$COMPARE_ROOT/ldd/ldd.${NAME}
    rm -rf "${TMP}"
    mkdir -p "${TMP}"
    
    (cd "${TMP}" && cp $OLD . && ${LDD} ${NAME} | awk '{ print $1;}' | sort | tee dep.old | uniq > dep.uniq.old)
    (cd "${TMP}" && cp $NEW . && ${LDD} ${NAME} | awk '{ print $1;}' | sort | tee dep.new | uniq > dep.uniq.new)
    (cd "${TMP}" && rm -f ${NAME})
    
    DIFFS_DEP=$(LANG=C diff "${TMP}/dep.old" "${TMP}/dep.new")
    DIFFS_UNIQ_DEP=$(LANG=C diff "${TMP}/dep.uniq.old" "${TMP}/dep.uniq.new")
    
    DEP_MSG=
    if [ -z "${DIFFS_UNIQ_DEP}" -a -z "${DIFFS_DEP}" ]; then
       DEP_MSG="Identical dependencies"
    elif [ -z "${DIFFS_UNIQ_DEP}" ]; then
       DEP_MSG="Redundant duplicate dependencies added"
       RES=1
    else
       DEP_MSG="DIFFERENT dependencies"
       RES=1
    fi
fi

if [ -n "$DIFFS" ]; then
   if [ $OLD_SIZE -ne $NEW_SIZE ]
   then
       echo Differences, content AND size     : $DEP_MSG : $OLD_NAME 
       RESULT=4
   else
       echo Differences, content BUT SAME size: $DEP_MSG : $OLD_NAME 
       RESULT=3
   fi
   if [ "x$VIEW" == "xview" ]; then
       LANG=C $DIFF $OLD_SYMBOLS $NEW_SYMBOLS
   fi
else
   if [ $OLD_SIZE -ne $NEW_SIZE ]
   then
       echo Identical symbols BUT NEW size    : $DEP_MSG : $OLD_NAME 
       RESULT=2
   else
       echo Identical symbols AND size, BUT not bytewise identical: $DEP_MSG : $OLD_NAME 
       RESULT=1
   fi
fi

exit $RESULT



