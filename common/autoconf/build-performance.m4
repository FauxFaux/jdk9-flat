#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

AC_DEFUN([BPERF_CHECK_CORES],
[
    AC_MSG_CHECKING([for number of cores])
    NUM_CORES=1
    FOUND_CORES=no
    
    if test -f /proc/cpuinfo; then
        # Looks like a Linux (or cygwin) system
        NUM_CORES=`cat /proc/cpuinfo  | grep -c processor`
        FOUND_CORES=yes
    elif test -x /usr/sbin/psrinfo; then
        # Looks like a Solaris system
        NUM_CORES=`LC_MESSAGES=C /usr/sbin/psrinfo -v | grep -c on-line`
        FOUND_CORES=yes
    elif test -x /usr/sbin/system_profiler; then
        # Looks like a MacOSX system
        NUM_CORES=`/usr/sbin/system_profiler -detailLevel full SPHardwareDataType | grep 'Cores' | awk  '{print [$]5}'`
        FOUND_CORES=yes
    elif test -n "$NUMBER_OF_PROCESSORS"; then
        # On windows, look in the env
        NUM_CORES=$NUMBER_OF_PROCESSORS
        FOUND_CORES=yes
    fi

    # For c/c++ code we run twice as many concurrent build
    # jobs than we have cores, otherwise we will stall on io.
    CONCURRENT_BUILD_JOBS=`expr $NUM_CORES \* 2`

    if test "x$FOUND_CORES" = xyes; then
        AC_MSG_RESULT([$NUM_CORES])
    else
        AC_MSG_RESULT([could not detect number of cores, defaulting to 1])
        AC_MSG_WARN([This will disable all parallelism from build!])
    fi 

])

AC_DEFUN([BPERF_CHECK_MEMORY_SIZE],
[
    AC_MSG_CHECKING([for memory size])
    # Default to 1024 MB
    MEMORY_SIZE=1024
    FOUND_MEM=no
    
    if test -f /proc/meminfo; then
        # Looks like a Linux (or cygwin) system
        MEMORY_SIZE=`cat /proc/meminfo | grep MemTotal | awk '{print [$]2}'`
        MEMORY_SIZE=`expr $MEMORY_SIZE / 1024`
        FOUND_MEM=yes
    elif test -x /usr/sbin/prtconf; then
        # Looks like a Solaris system
        MEMORY_SIZE=`/usr/sbin/prtconf | grep "Memory size" | awk '{ print [$]3 }'`
        FOUND_MEM=yes
    elif test -x /usr/sbin/system_profiler; then
        # Looks like a MacOSX system
        MEMORY_SIZE=`/usr/sbin/system_profiler -detailLevel full SPHardwareDataType | grep 'Memory' | awk  '{print [$]2}'`
        MEMORY_SIZE=`expr $MEMORY_SIZE \* 1024`
        FOUND_MEM=yes
    elif test "x$OPENJDK_BUILD_OS" = xwindows; then
        # Windows, but without cygwin
        MEMORY_SIZE=`wmic computersystem get totalphysicalmemory -value | grep = | cut -d "=" -f 2-`
        MEMORY_SIZE=`expr $MEMORY_SIZE / 1024 / 1024`
        FOUND_MEM=yes    
    fi

    if test "x$FOUND_MEM" = xyes; then
        AC_MSG_RESULT([$MEMORY_SIZE MB])
    else
        AC_MSG_RESULT([could not detect memory size, defaulting to 1024 MB])
        AC_MSG_WARN([This might seriously impact build performance!])
    fi 
])

AC_DEFUN_ONCE([BPERF_SETUP_BUILD_CORES],
[
# How many cores do we have on this build system?
AC_ARG_WITH(num-cores, [AS_HELP_STRING([--with-num-cores],
    [number of cores in the build system, e.g. --with-num-cores=8 @<:@probed@:>@])])
if test "x$with_num_cores" = x; then
    # The number of cores were not specified, try to probe them.
    BPERF_CHECK_CORES
else
    NUM_CORES=$with_num_cores
    CONCURRENT_BUILD_JOBS=`expr $NUM_CORES \* 2`
fi
AC_SUBST(NUM_CORES)
AC_SUBST(CONCURRENT_BUILD_JOBS)
])

AC_DEFUN_ONCE([BPERF_SETUP_BUILD_MEMORY],
[
# How much memory do we have on this build system?
AC_ARG_WITH(memory-size, [AS_HELP_STRING([--with-memory-size],
    [memory (in MB) available in the build system, e.g. --with-memory-size=1024 @<:@probed@:>@])])
if test "x$with_memory_size" = x; then
    # The memory size was not specified, try to probe it.
    BPERF_CHECK_MEMORY_SIZE
else
    MEMORY_SIZE=$with_memory_size
fi
AC_SUBST(MEMORY_SIZE)
])

AC_DEFUN([BPERF_SETUP_CCACHE],
[
    AC_ARG_ENABLE([ccache],
	      [AS_HELP_STRING([--disable-ccache],
	      		      [disable using ccache to speed up recompilations @<:@enabled@:>@])],
              [ENABLE_CCACHE=${enable_ccache}], [ENABLE_CCACHE=yes])
    if test "x$ENABLE_CCACHE" = xyes; then
        AC_PATH_PROG(CCACHE, ccache)
    else
        AC_MSG_CHECKING([for ccache])
        AC_MSG_RESULT([explicitly disabled])    
        CCACHE=
    fi    
    AC_SUBST(CCACHE)

    AC_ARG_WITH([ccache-dir],
	      [AS_HELP_STRING([--with-ccache-dir],
	      		      [where to store ccache files @<:@~/.ccache@:>@])])

    if test "x$with_ccache_dir" != x; then
        # When using a non home ccache directory, assume the use is to share ccache files
        # with other users. Thus change the umask.
        SET_CCACHE_DIR="CCACHE_DIR=$with_ccache_dir CCACHE_UMASK=002"
    fi
    CCACHE_FOUND=""
    if test "x$CCACHE" != x; then
        BPERF_SETUP_CCACHE_USAGE
    fi    
])

AC_DEFUN([BPERF_SETUP_CCACHE_USAGE],
[
    if test "x$CCACHE" != x; then
        CCACHE_FOUND="true"
        # Only use ccache if it is 3.1.4 or later, which supports
        # precompiled headers.
        AC_MSG_CHECKING([if ccache supports precompiled headers])
        HAS_GOOD_CCACHE=`($CCACHE --version | head -n 1 | grep -E 3.1.@<:@456789@:>@) 2> /dev/null`
        if test "x$HAS_GOOD_CCACHE" = x; then
            AC_MSG_RESULT([no, disabling ccache])
            CCACHE=
        else
            AC_MSG_RESULT([yes])
            AC_MSG_CHECKING([if C-compiler supports ccache precompiled headers])
            PUSHED_FLAGS="$CXXFLAGS"
            CXXFLAGS="-fpch-preprocess $CXXFLAGS"
            AC_COMPILE_IFELSE([AC_LANG_PROGRAM([], [])], [CC_KNOWS_CCACHE_TRICK=yes], [CC_KNOWS_CCACHE_TRICK=no])
            CXXFLAGS="$PUSHED_FLAGS"
            if test "x$CC_KNOWS_CCACHE_TRICK" = xyes; then
                AC_MSG_RESULT([yes])
            else
                AC_MSG_RESULT([no, disabling ccaching of precompiled headers])
                CCACHE=
            fi
        fi
    fi

    if test "x$CCACHE" != x; then
        CCACHE_SLOPPINESS=time_macros
        CCACHE="CCACHE_COMPRESS=1 $SET_CCACHE_DIR CCACHE_SLOPPINESS=$CCACHE_SLOPPINESS $CCACHE"
        CCACHE_FLAGS=-fpch-preprocess

        if test "x$SET_CCACHE_DIR" != x; then
            mkdir -p $CCACHE_DIR > /dev/null 2>&1
	    chmod a+rwxs $CCACHE_DIR > /dev/null 2>&1
        fi
    fi
])

AC_DEFUN_ONCE([BPERF_SETUP_PRECOMPILED_HEADERS],
[
       
###############################################################################
#
# Can the C/C++ compiler use precompiled headers?
#
AC_ARG_ENABLE([precompiled-headers], [AS_HELP_STRING([--disable-precompiled-headers],
	[disable using precompiled headers when compiling C++ @<:@enabled@:>@])],
    [ENABLE_PRECOMPH=${enable_precompiled_headers}], [ENABLE_PRECOMPH=yes])

USE_PRECOMPILED_HEADER=1
if test "x$ENABLE_PRECOMPH" = xno; then
    USE_PRECOMPILED_HEADER=0
fi

if test "x$ENABLE_PRECOMPH" = xyes; then
    # Check that the compiler actually supports precomp headers.
    if test "x$GCC" = xyes; then
         AC_MSG_CHECKING([that precompiled headers work])
         echo "int alfa();" > conftest.h
         $CXX -x c++-header conftest.h -o conftest.hpp.gch 2>&AS_MESSAGE_LOG_FD >&AS_MESSAGE_LOG_FD
         if test ! -f conftest.hpp.gch; then
             USE_PRECOMPILED_HEADER=0
             AC_MSG_RESULT([no])        
         else
             AC_MSG_RESULT([yes])
         fi
         rm -f conftest.h conftest.hpp.gch
    fi
fi

AC_SUBST(USE_PRECOMPILED_HEADER)
])


AC_DEFUN_ONCE([BPERF_SETUP_SMART_JAVAC],
[
AC_ARG_WITH(sjavac-server-java, [AS_HELP_STRING([--with-sjavac-server-java],
	[use this java binary for running the sjavac background server @<:@Boot JDK java@:>@])])

if test "x$with_sjavac_server_java" != x; then
    SJAVAC_SERVER_JAVA="$with_sjavac_server_java"
    FOUND_VERSION=`$SJAVAC_SERVER_JAVA -version 2>&1 | grep " version \""`
    if test "x$FOUND_VERSION" = x; then
        AC_MSG_ERROR([Could not execute server java: $SJAVAC_SERVER_JAVA])
    fi
else
    SJAVAC_SERVER_JAVA=""
    # Hotspot specific options.
    ADD_JVM_ARG_IF_OK([-verbosegc],SJAVAC_SERVER_JAVA,[$JAVA])
    # JRockit specific options.
    ADD_JVM_ARG_IF_OK([-Xverbose:gc],SJAVAC_SERVER_JAVA,[$JAVA])
    SJAVAC_SERVER_JAVA="$JAVA $SJAVAC_SERVER_JAVA"
fi                    
AC_SUBST(SJAVAC_SERVER_JAVA)

AC_ARG_WITH(sjavac-server-cores, [AS_HELP_STRING([--with-sjavac-server-cores],
	[use at most this number of concurrent threads on the sjavac server @<:@probed@:>@])])
if test "x$with_sjavac_server_cores" != x; then
    SJAVAC_SERVER_CORES="$with_sjavac_server_cores"
else
    if test "$NUM_CORES" -gt 16; then
        # We set this arbitrary limit because we want to limit the heap
        # size of the javac server.
        # In the future we will make the javac compilers in the server
        # share more and more state, thus enabling us to use more and
        # more concurrent threads in the server.
        SJAVAC_SERVER_CORES="16"
    else
        SJAVAC_SERVER_CORES="$NUM_CORES"
    fi

    if test "$MEMORY_SIZE" -gt "17000"; then
        MAX_HEAP_MEM=10000
        ADD_JVM_ARG_IF_OK([-d64],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms10G -Xmx10G],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "10000"; then
        MAX_HEAP_MEM=6000
        ADD_JVM_ARG_IF_OK([-d64],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms6G -Xmx6G],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "5000"; then
        MAX_HEAP_MEM=3000
        ADD_JVM_ARG_IF_OK([-d64],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
        ADD_JVM_ARG_IF_OK([-Xms1G -Xmx3G],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "3800"; then
        MAX_HEAP_MEM=2500
        ADD_JVM_ARG_IF_OK([-Xms1G -Xmx2500M],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "1900"; then
        MAX_HEAP_MEM=1200
        ADD_JVM_ARG_IF_OK([-Xms700M -Xmx1400M],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    elif test "$MEMORY_SIZE" -gt "1000"; then
        MAX_HEAP_MEM=900
        ADD_JVM_ARG_IF_OK([-Xms400M -Xmx1100M],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    else
        MAX_HEAP_MEM=512
        ADD_JVM_ARG_IF_OK([-Xms256M -Xmx512M],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    fi

    ADD_JVM_ARG_IF_OK([-XX:PermSize=32m],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    ADD_JVM_ARG_IF_OK([-XX:MaxPermSize=160m],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])
    ADD_JVM_ARG_IF_OK([-XX:ThreadStackSize=$STACK_SIZE],SJAVAC_SERVER_JAVA,[$SJAVAC_SERVER_JAVA])

    MAX_COMPILERS_IN_HEAP=`expr $MAX_HEAP_MEM / 501`
    if test "$SJAVAC_SERVER_CORES" -gt "$MAX_COMPILERS_IN_HEAP"; then
        AC_MSG_CHECKING([if number of server cores must be reduced])
        SJAVAC_SERVER_CORES="$MAX_COMPILERS_IN_HEAP"
        AC_MSG_RESULT([yes, to $SJAVAC_SERVER_CORES with max heap size $MAX_HEAP_MEM MB])
    fi
fi                    
AC_SUBST(SJAVAC_SERVER_CORES)

AC_MSG_CHECKING([whether to use sjavac])
AC_ARG_ENABLE([sjavac], [AS_HELP_STRING([--enable-sjavac],
	[use sjavac to do fast incremental compiles @<:@disabled@:>@])],
	[ENABLE_SJAVAC="${enableval}"], [ENABLE_SJAVAC='no'])
AC_MSG_RESULT([$ENABLE_SJAVAC])
AC_SUBST(ENABLE_SJAVAC)

if test "x$ENABLE_SJAVAC" = xyes; then
    SJAVAC_SERVER_DIR="$OUTPUT_ROOT/javacservers"
else
    SJAVAC_SERVER_DIR=
fi
AC_SUBST(SJAVAC_SERVER_DIR)

])
