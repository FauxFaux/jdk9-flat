#
# Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 4500504
# @summary If the context class loader is a child of the impl's class
# loader, the context class loader should remain unchanged (i.e., not be
# set to the impl's class loader) when the impl is activated.
# @library ../../../testlibrary
# @build TestLibrary RMID ActivationLibrary
# @build ExtLoadedImplTest ExtLoadedImpl ExtLoadedImpl_Stub CheckLoader
# @run shell ext.sh

mkdir -p classes
cp $TESTCLASSES/*.class classes
rm classes/ExtLoadedImpl.class classes/ExtLoadedImpl_Stub.class classes/CheckLoader.class
mkdir -p ext
$TESTJAVA/bin/jar cf ext/ext.jar -C $TESTCLASSES ExtLoadedImpl.class -C $TESTCLASSES ExtLoadedImpl_Stub.class -C $TESTCLASSES CheckLoader.class

$TESTJAVA/bin/java -cp classes -Dtest.src=$TESTSRC -Dtest.classes=$TESTCLASSES -Djava.security.policy=$TESTSRC/security.policy -Djava.ext.dirs=ext ExtLoadedImplTest

