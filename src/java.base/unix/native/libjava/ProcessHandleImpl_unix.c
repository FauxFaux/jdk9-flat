/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "jni.h"
#include "jni_util.h"
#include "java_lang_ProcessHandleImpl.h"
#include "java_lang_ProcessHandleImpl_Info.h"


#include <stdio.h>

#include <errno.h>
#include <fcntl.h>
#include <pwd.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>

#include <string.h>
#include <dirent.h>
#include <ctype.h>

/**
 * Implementations of ProcessHandleImpl functions that are common to all
 * Unix variants:
 * - waitForProcessExit0(pid, reap)
 * - getCurrentPid0()
 * - destroy0(pid, force)
 */

#ifndef WIFEXITED
#define WIFEXITED(status) (((status)&0xFF) == 0)
#endif

#ifndef WEXITSTATUS
#define WEXITSTATUS(status) (((status)>>8)&0xFF)
#endif

#ifndef WIFSIGNALED
#define WIFSIGNALED(status) (((status)&0xFF) > 0 && ((status)&0xFF00) == 0)
#endif

#ifndef WTERMSIG
#define WTERMSIG(status) ((status)&0x7F)
#endif

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == -1) && (errno == EINTR)); \
} while(0)

#define RESTARTABLE_RETURN_PTR(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == NULL) && (errno == EINTR)); \
} while(0)

#ifdef __solaris__
    #define STAT_FILE "/proc/%d/status"
#else
    #define STAT_FILE "/proc/%d/stat"
#endif

/* Block until a child process exits and return its exit code.
 * Note, can only be called once for any given pid if reapStatus = true.
 */
JNIEXPORT jint JNICALL
Java_java_lang_ProcessHandleImpl_waitForProcessExit0(JNIEnv* env,
                                                     jclass junk,
                                                     jlong jpid,
                                                     jboolean reapStatus)
{
    pid_t pid = (pid_t)jpid;
    errno = 0;

    if (reapStatus != JNI_FALSE) {
        /* Wait for the child process to exit.
         * waitpid() is standard, so use it on all POSIX platforms.
         * It is known to work when blocking to wait for the pid
         * This returns immediately if the child has already exited.
         */
        int status;
        while (waitpid(pid, &status, 0) < 0) {
            switch (errno) {
                case ECHILD: return 0;
                case EINTR: break;
                default: return -1;
            }
        }

        if (WIFEXITED(status)) {
            return WEXITSTATUS(status);
        } else if (WIFSIGNALED(status)) {
            /* The child exited because of a signal.
             * The best value to return is 0x80 + signal number,
             * because that is what all Unix shells do, and because
             * it allows callers to distinguish between process exit and
             * process death by signal.
             * Unfortunately, the historical behavior on Solaris is to return
             * the signal number, and we preserve this for compatibility. */
#ifdef __solaris__
            return WTERMSIG(status);
#else
            return 0x80 + WTERMSIG(status);
#endif
        } else {
            return status;
        }
     } else {
        /*
         * Wait for the child process to exit without reaping the exitValue.
         * waitid() is standard on all POSIX platforms.
         * Note: waitid on Mac OS X 10.7 seems to be broken;
         * it does not return the exit status consistently.
         */
        siginfo_t siginfo;
        int options = WEXITED |  WNOWAIT;
        memset(&siginfo, 0, sizeof siginfo);
        while (waitid(P_PID, pid, &siginfo, options) < 0) {
            switch (errno) {
            case ECHILD: return 0;
            case EINTR: break;
            default: return -1;
            }
        }

        if (siginfo.si_code == CLD_EXITED) {
             /*
              * The child exited normally; get its exit code.
              */
             return siginfo.si_status;
        } else if (siginfo.si_code == CLD_KILLED || siginfo.si_code == CLD_DUMPED) {
             /* The child exited because of a signal.
              * The best value to return is 0x80 + signal number,
              * because that is what all Unix shells do, and because
              * it allows callers to distinguish between process exit and
              * process death by signal.
              * Unfortunately, the historical behavior on Solaris is to return
              * the signal number, and we preserve this for compatibility. */
 #ifdef __solaris__
             return WTERMSIG(siginfo.si_status);
 #else
             return 0x80 + WTERMSIG(siginfo.si_status);
 #endif
        } else {
             /*
              * Unknown exit code; pass it through.
              */
             return siginfo.si_status;
        }
    }
}

/*
 * Class:     java_lang_ProcessHandleImpl
 * Method:    getCurrentPid0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_java_lang_ProcessHandleImpl_getCurrentPid0(JNIEnv *env, jclass clazz) {
    pid_t pid = getpid();
    return (jlong) pid;
}

/*
 * Class:     java_lang_ProcessHandleImpl
 * Method:    destroy0
 * Signature: (Z)Z
 */
JNIEXPORT jboolean JNICALL
Java_java_lang_ProcessHandleImpl_destroy0(JNIEnv *env,
                                          jobject obj,
                                          jlong jpid,
                                          jlong startTime,
                                          jboolean force) {
    pid_t pid = (pid_t) jpid;
    int sig = (force == JNI_TRUE) ? SIGKILL : SIGTERM;
    jlong start = Java_java_lang_ProcessHandleImpl_isAlive0(env, obj, jpid);

    if (start == startTime || start == 0 || startTime == 0) {
        return (kill(pid, sig) < 0) ? JNI_FALSE : JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

/**
 * Size of password or group entry when not available via sysconf
 */
#define ENT_BUF_SIZE   1024

/**
 * Return a strong username for the uid_t or null.
 */
jstring uidToUser(JNIEnv* env, uid_t uid) {
    int result = 0;
    int buflen;
    char* pwbuf;
    jstring name = NULL;

    /* allocate buffer for password record */
    buflen = (int)sysconf(_SC_GETPW_R_SIZE_MAX);
    if (buflen == -1)
        buflen = ENT_BUF_SIZE;
    pwbuf = (char*)malloc(buflen);
    if (pwbuf == NULL) {
        JNU_ThrowOutOfMemoryError(env, "Unable to open getpwent");
    } else {
        struct passwd pwent;
        struct passwd* p = NULL;

#ifdef __solaris__
        RESTARTABLE_RETURN_PTR(getpwuid_r(uid, &pwent, pwbuf, (size_t)buflen), p);
#else
        RESTARTABLE(getpwuid_r(uid, &pwent, pwbuf, (size_t)buflen, &p), result);
#endif

        // Return the Java String if a name was found
        if (result == 0 && p != NULL &&
            p->pw_name != NULL && *(p->pw_name) != '\0') {
            name = JNU_NewStringPlatform(env, p->pw_name);
        }
        free(pwbuf);
    }
    return name;
}

/**
 * Implementations of ProcessHandleImpl functions that are common to
 * (some) Unix variants:
 * - getProcessPids0(pid, pidArray, parentArray)
 */

#if defined(__linux__) || defined(__AIX__)

/*
 * Signatures for internal OS specific functions.
 */
static pid_t getStatInfo(JNIEnv *env, pid_t pid,
                                     jlong *totalTime, jlong* startTime);
static void getCmdlineInfo(JNIEnv *env, pid_t pid, jobject jinfo);
static long long getBoottime(JNIEnv *env);

jstring uidToUser(JNIEnv* env, uid_t uid);

/* Field id for jString 'command' in java.lang.ProcessHandleImpl.Info */
static jfieldID ProcessHandleImpl_Info_commandID;

/* Field id for jString[] 'arguments' in java.lang.ProcessHandleImpl.Info */
static jfieldID ProcessHandleImpl_Info_argumentsID;

/* Field id for jlong 'totalTime' in java.lang.ProcessHandleImpl.Info */
static jfieldID ProcessHandleImpl_Info_totalTimeID;

/* Field id for jlong 'startTime' in java.lang.ProcessHandleImpl.Info */
static jfieldID ProcessHandleImpl_Info_startTimeID;

/* Field id for jString 'user' in java.lang.ProcessHandleImpl.Info */
static jfieldID ProcessHandleImpl_Info_userID;

/* static value for clock ticks per second. */
static long clock_ticks_per_second;

/* A static offset in milliseconds since boot. */
static long long bootTime_ms;

/**************************************************************
 * Static method to initialize field IDs and the ticks per second rate.
 *
 * Class:     java_lang_ProcessHandleImpl_Info
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_lang_ProcessHandleImpl_00024Info_initIDs(JNIEnv *env, jclass clazz) {

    CHECK_NULL(ProcessHandleImpl_Info_commandID = (*env)->GetFieldID(env,
        clazz, "command", "Ljava/lang/String;"));
    CHECK_NULL(ProcessHandleImpl_Info_argumentsID = (*env)->GetFieldID(env,
        clazz, "arguments", "[Ljava/lang/String;"));
    CHECK_NULL(ProcessHandleImpl_Info_totalTimeID = (*env)->GetFieldID(env,
        clazz, "totalTime", "J"));
    CHECK_NULL(ProcessHandleImpl_Info_startTimeID = (*env)->GetFieldID(env,
        clazz, "startTime", "J"));
    CHECK_NULL(ProcessHandleImpl_Info_userID = (*env)->GetFieldID(env,
        clazz, "user", "Ljava/lang/String;"));
}

/**************************************************************
 * Static method to initialize the ticks per second rate.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    initNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_lang_ProcessHandleImpl_initNative(JNIEnv *env, jclass clazz) {
    clock_ticks_per_second = sysconf(_SC_CLK_TCK);
    bootTime_ms = getBoottime(env);
}

/*
 * Check if a process is alive.
 * Return the start time (ms since 1970) if it is available.
 * If the start time is not available return 0.
 * If the pid is invalid, return -1.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    isAlive0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_java_lang_ProcessHandleImpl_isAlive0(JNIEnv *env, jobject obj, jlong jpid) {
    pid_t pid = (pid_t) jpid;
    jlong startTime = 0L;
    jlong totalTime = 0L;
    pid_t ppid = getStatInfo(env, pid, &totalTime, &startTime);
    return (ppid <= 0) ? -1 : startTime;
}

/*
 * Returns the parent pid of the requested pid.
 * The start time of the process must match (or be ANY).
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    parent0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_java_lang_ProcessHandleImpl_parent0(JNIEnv *env,
                                        jobject obj,
                                        jlong jpid,
                                        jlong startTime) {
    pid_t pid = (pid_t) jpid;
    pid_t ppid;

    pid_t mypid = getpid();
    if (pid == mypid) {
        ppid = getppid();
    } else {
        jlong start = 0L;;
        jlong total = 0L;        // unused
        ppid = getStatInfo(env, pid, &total, &start);
        if (start != startTime && start != 0 && startTime != 0) {
            ppid = -1;
        }
    }
    return (jlong) ppid;
}

/*
 * Returns the children of the requested pid and optionally each parent.
 * Reads /proc and accumulates any process who parent pid matches.
 * The resulting pids are stored into the array of longs.
 * The number of pids is returned if they all fit.
 * If the array is too short, the negative of the desired length is returned. *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    getChildPids
 * Signature: (J[J[J)I
 */
JNIEXPORT jint JNICALL
Java_java_lang_ProcessHandleImpl_getProcessPids0(JNIEnv *env,
                                                 jclass clazz,
                                                 jlong jpid,
                                                 jlongArray jarray,
                                                 jlongArray jparentArray,
                                                 jlongArray jstimesArray) {

    DIR* dir;
    struct dirent* ptr;
    pid_t pid = (pid_t) jpid;
    jlong* pids = NULL;
    jlong* ppids = NULL;
    jlong* stimes = NULL;
    jsize parentArraySize = 0;
    jsize arraySize = 0;
    jsize stimesSize = 0;
    jsize count = 0;

    arraySize = (*env)->GetArrayLength(env, jarray);
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    if (jparentArray != NULL) {
        parentArraySize = (*env)->GetArrayLength(env, jparentArray);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);

        if (arraySize != parentArraySize) {
            JNU_ThrowIllegalArgumentException(env, "array sizes not equal");
            return 0;
        }
    }
    if (jstimesArray != NULL) {
        stimesSize = (*env)->GetArrayLength(env, jstimesArray);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);

        if (arraySize != stimesSize) {
            JNU_ThrowIllegalArgumentException(env, "array sizes not equal");
            return 0;
        }
    }

    /*
     * To locate the children we scan /proc looking for files that have a
     * position integer as a filename.
     */
    if ((dir = opendir("/proc")) == NULL) {
        JNU_ThrowByNameWithLastError(env,
            "java/lang/RuntimeException", "Unable to open /proc");
        return -1;
    }

    do { // Block to break out of on Exception
        pids = (*env)->GetLongArrayElements(env, jarray, NULL);
        if (pids == NULL) {
            break;
        }
        if (jparentArray != NULL) {
            ppids  = (*env)->GetLongArrayElements(env, jparentArray, NULL);
            if (ppids == NULL) {
                break;
            }
        }
        if (jstimesArray != NULL) {
            stimes  = (*env)->GetLongArrayElements(env, jstimesArray, NULL);
            if (stimes == NULL) {
                break;
            }
        }

        while ((ptr = readdir(dir)) != NULL) {
            pid_t ppid = 0;
            jlong totalTime = 0L;
            jlong startTime = 0L;

            /* skip files that aren't numbers */
            pid_t childpid = (pid_t) atoi(ptr->d_name);
            if ((int) childpid <= 0) {
                continue;
            }
            // Read /proc/pid/stat and get the parent pid, and start time
            ppid = getStatInfo(env, childpid, &totalTime, &startTime);
            if (ppid > 0 && (pid == 0 || ppid == pid)) {
                if (count < arraySize) {
                    // Only store if it fits
                    pids[count] = (jlong) childpid;

                    if (ppids != NULL) {
                        // Store the parentPid
                        ppids[count] = (jlong) ppid;
                    }
                    if (stimes != NULL) {
                        // Store the process start time
                        stimes[count] = startTime;
                    }
                }
                count++; // Count to tabulate size needed
            }
        }
    } while (0);

    if (pids != NULL) {
        (*env)->ReleaseLongArrayElements(env, jarray, pids, 0);
    }
    if (ppids != NULL) {
        (*env)->ReleaseLongArrayElements(env, jparentArray, ppids, 0);
    }
    if (stimes != NULL) {
        (*env)->ReleaseLongArrayElements(env, jstimesArray, stimes, 0);
    }

    closedir(dir);
    // If more pids than array had size for; count will be greater than array size
    return count;
}


/**************************************************************
 * Implementation of ProcessHandleImpl_Info native methods.
 */

/*
 * Fill in the Info object from the OS information about the process.
 *
 * Class:     java_lang_ProcessHandleImpl_Info
 * Method:    info0
 * Signature: (JLjava/lang/ProcessHandle/Info;)I
 */
JNIEXPORT void JNICALL
Java_java_lang_ProcessHandleImpl_00024Info_info0(JNIEnv *env,
                                                 jobject jinfo,
                                                 jlong jpid) {
    pid_t pid = (pid_t) jpid;
    pid_t ppid;
    jlong totalTime = 0L;
    jlong startTime = -1L;

    ppid = getStatInfo(env, pid,  &totalTime, &startTime);
    if (ppid > 0) {
        (*env)->SetLongField(env, jinfo, ProcessHandleImpl_Info_totalTimeID, totalTime);
        JNU_CHECK_EXCEPTION(env);

        (*env)->SetLongField(env, jinfo, ProcessHandleImpl_Info_startTimeID, startTime);
        JNU_CHECK_EXCEPTION(env);

        getCmdlineInfo(env, pid, jinfo);
    }
}

/**
 * Read /proc/<pid>/stat and return the ppid, total cputime and start time.
 * -1 is fail;  zero is unknown; >  0 is parent pid
 */
static pid_t getStatInfo(JNIEnv *env, pid_t pid,
                                     jlong *totalTime, jlong* startTime) {
    FILE* fp;
    char buffer[2048];
    int statlen;
    char fn[32];
    char* s;
    int parentPid;
    long unsigned int utime = 0;      // clock tics
    long unsigned int stime = 0;      // clock tics
    long long unsigned int start = 0; // microseconds

    /*
     * Try to stat and then open /proc/%d/stat
     */
    snprintf(fn, sizeof fn, STAT_FILE, pid);

    fp = fopen(fn, "r");
    if (fp == NULL) {
        return -1;              // fail, no such /proc/pid/stat
    }

    /*
     * The format is: pid (command) state ppid ...
     * As the command could be anything we must find the right most
     * ")" and then skip the white spaces that follow it.
     */
    statlen = fread(buffer, 1, (sizeof buffer - 1), fp);
    fclose(fp);
    if (statlen < 0) {
        return 0;               // parent pid is not available
    }

    buffer[statlen] = '\0';
    s = strchr(buffer, '(');
    if (s == NULL) {
        return 0;               // parent pid is not available
    }
    // Found start of command, skip to end
    s++;
    s = strrchr(s, ')');
    if (s == NULL) {
        return 0;               // parent pid is not available
    }
    s++;

    // Scan the needed fields from status, retaining only ppid(4),
    // utime (14), stime(15), starttime(22)
    if (4 != sscanf(s, " %*c %d %*d %*d %*d %*d %*d %*u %*u %*u %*u %lu %lu %*d %*d %*d %*d %*d %*d %llu",
            &parentPid, &utime, &stime, &start)) {
        return 0;              // not all values parsed; return error
    }

    *totalTime = (utime + stime) * (jlong)(1000000000 / clock_ticks_per_second);

    *startTime = bootTime_ms + ((start * 1000) / clock_ticks_per_second);

    return parentPid;
}

/**
 * Construct the argument array by parsing the arguments from the sequence
 * of arguments. The zero'th arg is the command executable
 */
static int fillArgArray(JNIEnv *env, jobject jinfo,
                        int nargs, char *cp, char *argsEnd, jstring cmdexe) {
    jobject argsArray;
    int i;

    if (nargs < 1) {
        return 0;
    }

    if (cmdexe == NULL) {
        // Create a string from arg[0]
        CHECK_NULL_RETURN((cmdexe = JNU_NewStringPlatform(env, cp)), -1);
    }
    (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_commandID, cmdexe);
    JNU_CHECK_EXCEPTION_RETURN(env, -3);

    // Create a String array for nargs-1 elements
    argsArray = (*env)->NewObjectArray(env, nargs - 1, JNU_ClassString(env), NULL);
    CHECK_NULL_RETURN(argsArray, -1);

    for (i = 0; i < nargs - 1; i++) {
        jstring str = NULL;

        cp += strnlen(cp, (argsEnd - cp)) + 1;
        if (cp > argsEnd || *cp == '\0') {
            return -2;  // Off the end pointer or an empty argument is an error
        }

        CHECK_NULL_RETURN((str = JNU_NewStringPlatform(env, cp)), -1);

        (*env)->SetObjectArrayElement(env, argsArray, i, str);
        JNU_CHECK_EXCEPTION_RETURN(env, -3);
    }
    (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_argumentsID, argsArray);
    JNU_CHECK_EXCEPTION_RETURN(env, -4);
    return 0;
}


static void getCmdlineInfo(JNIEnv *env, pid_t pid, jobject jinfo) {
    int fd;
    int cmdlen = 0;
    char *cmdline = NULL, *cmdEnd;  // used for command line args and exe
    jstring cmdexe = NULL;
    char fn[32];
    struct stat stat_buf;

    /*
     * Try to open /proc/%d/cmdline
     */
    snprintf(fn, sizeof fn, "/proc/%d/cmdline", pid);
    if ((fd = open(fn, O_RDONLY)) < 0) {
        return;
    }

    do {                // Block to break out of on errors
        int i;
        char *s;

        cmdline = (char*)malloc(PATH_MAX);
        if (cmdline == NULL) {
            break;
        }

        /*
         * The path to the executable command is the link in /proc/<pid>/exe.
         */
        snprintf(fn, sizeof fn, "/proc/%d/exe", pid);
        if ((cmdlen = readlink(fn, cmdline, PATH_MAX - 1)) > 0) {
            // null terminate and create String to store for command
            cmdline[cmdlen] = '\0';
            cmdexe = JNU_NewStringPlatform(env, cmdline);
            (*env)->ExceptionClear(env);        // unconditionally clear any exception
        }

        /*
         * The buffer format is the arguments nul terminated with an extra nul.
         */
        cmdlen = read(fd, cmdline, PATH_MAX-1);
        if (cmdlen < 0) {
            break;
        }

        // Terminate the buffer and count the arguments
        cmdline[cmdlen] = '\0';
        cmdEnd = &cmdline[cmdlen + 1];
        for (s = cmdline,i = 0; *s != '\0' && (s < cmdEnd); i++) {
            s += strnlen(s, (cmdEnd - s)) + 1;
        }

        if (fillArgArray(env, jinfo, i, cmdline, cmdEnd, cmdexe) < 0) {
            break;
        }

        // Get and store the user name
        if (fstat(fd, &stat_buf) == 0) {
            jstring name = uidToUser(env, stat_buf.st_uid);
            if (name != NULL) {
                (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_userID, name);
            }
        }
    } while (0);

    if (cmdline != NULL) {
        free(cmdline);
    }
    if (fd >= 0) {
        close(fd);
    }
}

/**
 * Read the boottime from /proc/stat.
 */
static long long getBoottime(JNIEnv *env) {
    FILE *fp;
    char *line = NULL;
    size_t len = 0;
    long long bootTime = 0;

    fp = fopen("/proc/stat", "r");
    if (fp == NULL) {
        return -1;
    }

    while (getline(&line, &len, fp) != -1) {
        if (sscanf(line, "btime %llu", &bootTime) == 1) {
            break;
        }
    }
    free(line);

    if (fp != 0) {
        fclose(fp);
    }

    return bootTime * 1000;
}

#endif  //  defined(__linux__) || defined(__AIX__)


/* Block until a child process exits and return its exit code.
   Note, can only be called once for any given pid. */
JNIEXPORT jint JNICALL
Java_java_lang_ProcessImpl_waitForProcessExit(JNIEnv* env,
                                              jobject junk,
                                              jint pid)
{
    /* We used to use waitid() on Solaris, waitpid() on Linux, but
     * waitpid() is more standard, so use it on all POSIX platforms. */
    int status;
    /* Wait for the child process to exit.  This returns immediately if
       the child has already exited. */
    while (waitpid(pid, &status, 0) < 0) {
        switch (errno) {
        case ECHILD: return 0;
        case EINTR: break;
        default: return -1;
        }
    }

    if (WIFEXITED(status)) {
        /*
         * The child exited normally; get its exit code.
         */
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        /* The child exited because of a signal.
         * The best value to return is 0x80 + signal number,
         * because that is what all Unix shells do, and because
         * it allows callers to distinguish between process exit and
         * process death by signal.
         * Unfortunately, the historical behavior on Solaris is to return
         * the signal number, and we preserve this for compatibility. */
#ifdef __solaris__
        return WTERMSIG(status);
#else
        return 0x80 + WTERMSIG(status);
#endif
    } else {
        /*
         * Unknown exit code; pass it through.
         */
        return status;
    }
}


