#
# Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test
# @bug 5094825
# @summary verify no deadlock if crypto provider in other classloader is used to verify signed jars
#
# @run shell/timeout=30 ClassLoaderDeadlock.sh

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory
if [ "${TESTSRC}" = "" ] ; then
   TESTSRC="."
fi

if [ "${TESTCLASSES}" = "" ] ; then
   TESTCLASSES="."
fi

if [ "${TESTJAVA}" = "" ] ; then
   echo "TESTJAVA not set.  Test cannot execute."
   echo "FAILED!!!"
   exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS )
    PATHSEP=":"
    FILESEP="/"
    ;;
  Linux )
    PATHSEP=":"
    FILESEP="/"
    ;;
  Windows* )
    PATHSEP=";"
    FILESEP="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# remove old class files
cd ${TESTCLASSES}${FILESEP}
rm -f ClassLoaderDeadlock.class
rm -rf provider
mkdir provider

# compile the test program
${TESTJAVA}${FILESEP}bin${FILESEP}javac \
	-d ${TESTCLASSES}${FILESEP} \
	${TESTSRC}${FILESEP}ClassLoaderDeadlock.java

${TESTJAVA}${FILESEP}bin${FILESEP}javac \
	-d ${TESTCLASSES}${FILESEP}provider${FILESEP} \
	${TESTSRC}${FILESEP}provider${FILESEP}HashProvider.java

# run the test
${TESTJAVA}${FILESEP}bin${FILESEP}java \
	-classpath ${TESTCLASSES}${PATHSEP}${TESTSRC}${FILESEP}Deadlock.jar \
	ClassLoaderDeadlock

exit $?
