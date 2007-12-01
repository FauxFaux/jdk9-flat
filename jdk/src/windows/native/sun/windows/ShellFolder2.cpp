/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#define OEMRESOURCE

#ifdef DEBUG
// Warning : do not depend on anything in <awt.h>.  Including this file
// is a fix for 4507525 to use the same operator new and delete as AWT.
// This file should stand independent of AWT and should ultimately be
// put into its own DLL.
#include <awt.h>
#endif

#include <windows.h>
#include <shlobj.h>
#include <shellapi.h>
#include "jni_util.h"
#include "jlong.h"
#include "alloc.h"

#include "stdhdrs.h"
#include "UnicowsLoader.h"

// {93F2F68C-1D1B-11d3-A30E-00C04F79ABD1}
DEFINE_GUID(IID_IShellFolder2, 0x93f2f68c, 0x1d1b, 0x11d3, 0xa3, 0xe, 0x0, 0xc0, 0x4f, 0x79, 0xab, 0xd1);

#undef IID_IShellLinkW
#undef IID_IExtractIconW
// copied from shlguid.h
DEFINE_SHLGUID(IID_IShellLinkW,         0x000214F9L, 0, 0);
DEFINE_SHLGUID(IID_IExtractIconW,       0x000214FAL, 0, 0);

//#include <sun_awt_shell_Win32ShellFolder2.h>

// Shell Functions
typedef BOOL (WINAPI *DestroyIconType)(HICON);
typedef HINSTANCE (WINAPI *FindExecutableType)(LPCTSTR,LPCTSTR,LPTSTR);
typedef HICON (WINAPI *ImageList_GetIconType)(HIMAGELIST,int,UINT);
typedef BOOL (WINAPI *GetIconInfoType)(HICON,PICONINFO);
typedef HRESULT (WINAPI *SHGetDesktopFolderType)(IShellFolder**);
typedef DWORD* (WINAPI *SHGetFileInfoType)(LPCTSTR,DWORD,SHFILEINFO*,UINT,UINT);
typedef HRESULT (WINAPI *SHGetMallocType)(IMalloc**);
typedef BOOL (WINAPI *SHGetPathFromIDListType)(LPCITEMIDLIST,LPTSTR);
typedef HRESULT (WINAPI *SHGetSpecialFolderLocationType)(HWND,int,LPITEMIDLIST*);

static DestroyIconType fn_DestroyIcon;
static FindExecutableType fn_FindExecutable;
static GetIconInfoType fn_GetIconInfo;
static ImageList_GetIconType fn_ImageList_GetIcon;
static SHGetDesktopFolderType fn_SHGetDesktopFolder;
static SHGetFileInfoType fn_SHGetFileInfo;
static SHGetMallocType fn_SHGetMalloc;
static SHGetPathFromIDListType fn_SHGetPathFromIDList;
static SHGetSpecialFolderLocationType fn_SHGetSpecialFolderLocation;

// Field IDs
static jmethodID MID_pIShellFolder;
static jfieldID FID_pIShellIcon;
static jmethodID MID_relativePIDL;
static jfieldID FID_displayName;
static jfieldID FID_folderType;

// Other statics
static IMalloc* pMalloc;
static IShellFolder* pDesktop;

static BOOL isXP;

// copied from awt.h, because it is not included in release
#if defined (WIN32)
    #define IS_WINVISTA (!(::GetVersion() & 0x80000000) && LOBYTE(LOWORD(::GetVersion())) >= 6)
#else
    #define IS_WINVISTA FALSE
#endif


extern "C" {

static BOOL initShellProcs()
{
    static HMODULE libShell32 = NULL;
    static HMODULE libUser32 = NULL;
    static HMODULE libComCtl32 = NULL;
    static HMODULE libUnicows = UnicowsLoader::GetModuleHandle();
    // If already initialized, return TRUE
    if (libShell32 != NULL && libUser32 != NULL) {
        return TRUE;
    }
    // Load libraries
    libShell32 = LoadLibrary(TEXT("shell32.dll"));
    if (libShell32 == NULL) {
        return FALSE;
    }
    libUser32 = LoadLibrary(TEXT("user32.dll"));
    if (libUser32 == NULL) {
        return FALSE;
    }
    libComCtl32 = LoadLibrary(TEXT("comctl32.dll"));
    if (libComCtl32 == NULL) {
        return FALSE;
    }

    // Set up procs - libComCtl32
    fn_ImageList_GetIcon = (ImageList_GetIconType)GetProcAddress(libComCtl32, "ImageList_GetIcon");
    if (fn_ImageList_GetIcon == NULL) {
        return FALSE;
    }

    // Set up procs - libShell32
        fn_FindExecutable = (FindExecutableType)GetProcAddress(
                (libUnicows ? libUnicows : libShell32), "FindExecutableW");
    if (fn_FindExecutable == NULL) {
        return FALSE;
    }
        fn_SHGetDesktopFolder = (SHGetDesktopFolderType)GetProcAddress(libShell32,
                "SHGetDesktopFolder");
    if (fn_SHGetDesktopFolder == NULL) {
        return FALSE;
    }
        fn_SHGetFileInfo = (SHGetFileInfoType)GetProcAddress(
                (libUnicows ? libUnicows : libShell32), "SHGetFileInfoW");
    if (fn_SHGetFileInfo == NULL) {
        return FALSE;
    }
        fn_SHGetMalloc = (SHGetMallocType)GetProcAddress(libShell32,
        "SHGetMalloc");
    if (fn_SHGetMalloc == NULL) {
        return FALSE;
    }
    // Set up IMalloc
    if (fn_SHGetMalloc(&pMalloc) != S_OK) {
        return FALSE;
    }
        fn_SHGetPathFromIDList = (SHGetPathFromIDListType)GetProcAddress(
                (libUnicows ? libUnicows : libShell32), "SHGetPathFromIDListW");
    if (fn_SHGetPathFromIDList == NULL) {
        return FALSE;
    }
        fn_SHGetSpecialFolderLocation = (SHGetSpecialFolderLocationType)
        GetProcAddress(libShell32, "SHGetSpecialFolderLocation");
    if (fn_SHGetSpecialFolderLocation == NULL) {
        return FALSE;
    }

    // Set up procs - libUser32
    fn_GetIconInfo = (GetIconInfoType)GetProcAddress(libUser32, "GetIconInfo");
    if (fn_GetIconInfo == NULL) {
        return FALSE;
    }
    fn_DestroyIcon = (DestroyIconType)GetProcAddress(libUser32, "DestroyIcon");
    if (fn_DestroyIcon == NULL) {
        return FALSE;
    }
    return TRUE;
}

// To call real JNU_NewStringPlatform
#undef JNU_NewStringPlatform
static jstring jstringFromSTRRET(JNIEnv* env, LPITEMIDLIST pidl, STRRET* pStrret) {
    switch (pStrret->uType) {
        case STRRET_CSTR :
            return JNU_NewStringPlatform(env, pStrret->cStr);
        case STRRET_OFFSET :
            // Note : this may need to be WCHAR instead
            return JNU_NewStringPlatform(env,
                                         (CHAR*)pidl + pStrret->uOffset);
        case STRRET_WSTR :
            return env->NewString(pStrret->pOleStr,
                static_cast<jsize>(wcslen(pStrret->pOleStr)));
    }
    return NULL;
}
// restoring the original definition
#define JNU_NewStringPlatform(env, x) env->NewString(x, static_cast<jsize>(_tcslen(x)))

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_shell_Win32ShellFolder2_initIDs
    (JNIEnv* env, jclass cls)
{
    if (!initShellProcs()) {
        JNU_ThrowInternalError(env, "Could not initialize shell library");
        return;
    }
    MID_pIShellFolder = env->GetMethodID(cls, "setIShellFolder", "(J)V");
    FID_pIShellIcon = env->GetFieldID(cls, "pIShellIcon", "J");
    MID_relativePIDL = env->GetMethodID(cls, "setRelativePIDL", "(J)V");
    FID_displayName = env->GetFieldID(cls, "displayName", "Ljava/lang/String;");
    FID_folderType = env->GetFieldID(cls, "folderType", "Ljava/lang/String;");

    // Find out if we are on XP or later
    long version = GetVersion();
    isXP = (!(version & 0x80000000) &&
            (LOBYTE(LOWORD(version)) == 5 &&
             HIBYTE(LOWORD(version)) >= 1) ||
            LOBYTE(LOWORD(version)) > 5);
}

static IShellIcon* getIShellIcon(IShellFolder* pIShellFolder) {
    // http://msdn.microsoft.com/library/en-us/shellcc/platform/Shell/programmersguide/shell_int/shell_int_programming/std_ifaces.asp
    HRESULT hres;
    HICON hIcon = NULL;
    IShellIcon* pIShellIcon;
    if (pIShellFolder != NULL) {
        hres = pIShellFolder->QueryInterface(IID_IShellIcon, (void**)&pIShellIcon);
        if (SUCCEEDED(hres)) {
            return pIShellIcon;
        }
    }
    return (IShellIcon*)NULL;
}

// Fixed 6263669
//
// CoInitialize wrapper
// call CoInitialize to initialize COM in STA mode and check result
// RPC_E_CHANGED_MODE means COM has already been initialized in MTA mode,
// so don't set the flag to call CoUninitialize later

BOOL CoInit(BOOL& doCoUninit) { // returns TRUE if initialized successfully
    switch(::CoInitialize(NULL)) {
    case S_OK:
    case S_FALSE:
        doCoUninit = TRUE;
        return TRUE;
        break;
    case RPC_E_CHANGED_MODE:
        doCoUninit = FALSE;
        return TRUE;
        break;
    default:
        return FALSE;
    }
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getIShellIcon
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getIShellIcon
    (JNIEnv* env, jclass cls, jlong parentIShellFolder)
{
    return (jlong)getIShellIcon((IShellFolder*)parentIShellFolder);
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    initDesktop
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_shell_Win32ShellFolder2_initDesktop
    (JNIEnv* env, jobject desktop)
{
    // Get desktop IShellFolder
    HRESULT res = fn_SHGetDesktopFolder(&pDesktop);
    if (res != S_OK) {
        JNU_ThrowInternalError(env, "Could not get desktop shell folder");
        return;
    }
    // Set field ID for pIShellFolder
    env->CallVoidMethod(desktop, MID_pIShellFolder, (jlong)pDesktop);
    // Get desktop relative PIDL
    LPITEMIDLIST relPIDL;
    res = fn_SHGetSpecialFolderLocation(NULL, CSIDL_DESKTOP, &relPIDL);
    if (res != S_OK) {
        JNU_ThrowInternalError(env,
            "Could not get desktop shell folder ID list");
        return;
    }
    // Set field ID for relative PIDL
    env->CallVoidMethod(desktop, MID_relativePIDL, (jlong)relPIDL);
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    initSpecial
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_sun_awt_shell_Win32ShellFolder2_initSpecial
    (JNIEnv* env, jobject folder, jlong desktopIShellFolder, jint folderType)
{
    // Get desktop IShellFolder interface
    IShellFolder* pDesktop = (IShellFolder*)desktopIShellFolder;
    if (pDesktop == NULL) {
        JNU_ThrowInternalError(env, "Desktop shell folder missing");
        return;
    }
    // Get special folder relative PIDL
    LPITEMIDLIST relPIDL;
    HRESULT res = fn_SHGetSpecialFolderLocation(NULL, folderType,
        &relPIDL);
    if (res != S_OK) {
        JNU_ThrowIOException(env, "Could not get shell folder ID list");
        return;
    }
    // Set field ID for relative PIDL
    env->CallVoidMethod(folder, MID_relativePIDL, (jlong)relPIDL);
    // Get special folder IShellFolder interface
    IShellFolder* pFolder;
    res = pDesktop->BindToObject(relPIDL, NULL, IID_IShellFolder,
        (void**)&pFolder);
    if (res != S_OK) {
        JNU_ThrowInternalError(env,
            "Could not bind shell folder to interface");
        return;
    }
    // Set field ID for pIShellFolder
    env->CallVoidMethod(folder, MID_pIShellFolder, (jlong)pFolder);
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getNextPIDLEntry
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getNextPIDLEntry
    (JNIEnv* env, jclass cls, jlong jpIDL)
{
    LPITEMIDLIST pIDL = (LPITEMIDLIST)jpIDL;

    // Check for valid pIDL.
    if(pIDL == NULL)
        return NULL;

    // Get the size of the specified item identifier.
    int cb = pIDL->mkid.cb;

    // If the size is zero, it is the end of the list.
    if (cb == 0)
        return NULL;

    // Add cb to pidl (casting to increment by bytes).
    pIDL = (LPITEMIDLIST)(((LPBYTE)pIDL) + cb);

    // Return NULL if it is null-terminating, or a pidl otherwise.
    return (pIDL->mkid.cb == 0) ? 0 : (jlong)pIDL;
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    copyFirstPIDLEntry
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_copyFirstPIDLEntry
    (JNIEnv* env, jclass cls, jlong jpIDL)
{
    LPITEMIDLIST pIDL = (LPITEMIDLIST)jpIDL;
    if (pIDL == NULL) {
        return 0;
    }
    // Get the size of the specified item identifier.
    int cb = pIDL->mkid.cb;

    // If the size is zero, it is the end of the list.
    if (cb == 0)
        return 0;

    // Allocate space for this as well as null-terminating entry.
    LPITEMIDLIST newPIDL = (LPITEMIDLIST)pMalloc->Alloc(cb + sizeof(SHITEMID));

    // Copy data.
    memcpy(newPIDL, pIDL, cb);

    // Set null terminator for next entry.
    LPITEMIDLIST nextPIDL = (LPITEMIDLIST)(((LPBYTE)newPIDL) + cb);
    nextPIDL->mkid.cb = 0;

    return (jlong)newPIDL;
}

static int pidlLength(LPITEMIDLIST pIDL) {
    int len = 0;
    while (pIDL->mkid.cb != 0) {
        int cb = pIDL->mkid.cb;
        len += cb;
        pIDL = (LPITEMIDLIST)(((LPBYTE)pIDL) + cb);
    }
    return len;
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    combinePIDLs
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_combinePIDLs
    (JNIEnv* env, jclass cls, jlong jppIDL, jlong jpIDL)
{
    // Combine an absolute (fully qualified) pidl in a parent with the relative
    // pidl of a child object to create a new absolute pidl for the child.

    LPITEMIDLIST parentPIDL   = (LPITEMIDLIST)jppIDL;
    LPITEMIDLIST relativePIDL = (LPITEMIDLIST)jpIDL;

    int len1 = pidlLength(parentPIDL);
    int len2 = pidlLength(relativePIDL);

    LPITEMIDLIST newPIDL = (LPITEMIDLIST)pMalloc->Alloc(len1 + len2 + sizeof(SHITEMID));
    memcpy(newPIDL, parentPIDL, len1);
    memcpy(((LPBYTE) newPIDL) + len1, relativePIDL, len2);
    LPITEMIDLIST nullTerminator = (LPITEMIDLIST)(((LPBYTE) newPIDL) + len1 + len2);
    nullTerminator->mkid.cb = 0;

    return (jlong) newPIDL;
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    releasePIDL
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_shell_Win32ShellFolder2_releasePIDL
    (JNIEnv* env, jclass cls, jlong pIDL)
{
    if (pIDL != 0L) {
        pMalloc->Free((LPITEMIDLIST)pIDL);
    }
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    releaseIShellFolder
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_shell_Win32ShellFolder2_releaseIShellFolder
    (JNIEnv* env, jclass cls, jlong pIShellFolder)
{
    if (pIShellFolder != 0L) {
        ((IShellFolder*)pIShellFolder)->Release();
    }
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    compareIDs
 * Signature: (JJJ)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_shell_Win32ShellFolder2_compareIDs
    (JNIEnv* env, jclass cls, jlong jpParentIShellFolder, jlong pIDL1, jlong pIDL2)
{
    IShellFolder* pParentIShellFolder = (IShellFolder*)jpParentIShellFolder;
    if (pParentIShellFolder == NULL) {
        return 0;
    }
    return pParentIShellFolder->CompareIDs(0, (LPCITEMIDLIST) pIDL1, (LPCITEMIDLIST) pIDL2);
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getAttributes0
 * Signature: (JJI)J
 */
JNIEXPORT jint JNICALL Java_sun_awt_shell_Win32ShellFolder2_getAttributes0
    (JNIEnv* env, jclass cls, jlong jpParentIShellFolder, jlong jpIDL, jint attrsMask)
{
    IShellFolder* pParentIShellFolder = (IShellFolder*)jpParentIShellFolder;
    if (pParentIShellFolder == NULL) {
        return 0;
    }
    LPCITEMIDLIST pIDL = (LPCITEMIDLIST)jpIDL;
    if (pIDL == NULL) {
        return 0;
    }
    ULONG attrs = attrsMask;
    HRESULT res = pParentIShellFolder->GetAttributesOf(1, &pIDL, &attrs);
    return attrs;
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getFileSystemPath
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_shell_Win32ShellFolder2_getFileSystemPath__I
    (JNIEnv* env, jclass cls, jint csidl)
{
    LPITEMIDLIST relPIDL;
    TCHAR szBuf[MAX_PATH];
    HRESULT res = fn_SHGetSpecialFolderLocation(NULL, csidl, &relPIDL);
    if (res != S_OK) {
        JNU_ThrowIOException(env, "Could not get shell folder ID list");
        return NULL;
    }
    if (fn_SHGetPathFromIDList(relPIDL, szBuf)) {
        return JNU_NewStringPlatform(env, szBuf);
    } else {
        return NULL;
    }
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getEnumObjects
 * Signature: (JZ)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getEnumObjects
    (JNIEnv* env, jobject folder, jlong pIShellFolder,
     jboolean isDesktop, jboolean includeHiddenFiles)
{
    IShellFolder* pFolder = (IShellFolder*)pIShellFolder;
    if (pFolder == NULL) {
        return 0;
    }
    DWORD dwFlags = SHCONTF_FOLDERS | SHCONTF_NONFOLDERS;
    if (includeHiddenFiles) {
        dwFlags |= SHCONTF_INCLUDEHIDDEN;
    }
        /*
    if (!isDesktop) {
        dwFlags = dwFlags | SHCONTF_NONFOLDERS;
    }
        */
    IEnumIDList* pEnum;
    if (pFolder->EnumObjects(NULL, dwFlags, &pEnum) != S_OK) {
        return 0;
    }
    return (jlong)pEnum;
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getNextChild
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getNextChild
    (JNIEnv* env, jobject folder, jlong pEnumObjects)
{
    IEnumIDList* pEnum = (IEnumIDList*)pEnumObjects;
    if (pEnum == NULL) {
        return 0;
    }
    LPITEMIDLIST pidl;
    if (pEnum->Next(1, &pidl, NULL) != S_OK) {
        return 0;
    }
    return (jlong)pidl;
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    releaseEnumObjects
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_shell_Win32ShellFolder2_releaseEnumObjects
    (JNIEnv* env, jobject folder, jlong pEnumObjects)
{
    IEnumIDList* pEnum = (IEnumIDList*)pEnumObjects;
    if (pEnum == NULL) {
        return;
    }
    pEnum->Release();
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    bindToObject
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_bindToObject
    (JNIEnv* env, jclass cls, jlong parentIShellFolder, jlong relativePIDL)
{
    IShellFolder* pParent = (IShellFolder*)parentIShellFolder;
    if (pParent == NULL) {
        return 0;
    }
    LPITEMIDLIST pidl = (LPITEMIDLIST)relativePIDL;
    if (pidl == NULL) {
        return 0;
    }
    IShellFolder* pFolder;
    HRESULT hr = pParent->BindToObject(pidl, NULL, IID_IShellFolder, (void**)&pFolder);
    if (SUCCEEDED (hr)) {
        return (jlong)pFolder;
    }
    if (IS_WINVISTA) {
        BOOL doCoUninit;
        if (CoInit(doCoUninit)) {
            hr = pParent->BindToObject(pidl, NULL, IID_IShellFolder, (void**)&pFolder);
            if (doCoUninit) {
                ::CoUninitialize();
            }
            if (SUCCEEDED (hr)) {
                return (jlong)pFolder;
            }
        }
    }
    return 0;
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getLinkLocation
 * Signature: (JJZ)J;
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getLinkLocation
    (JNIEnv* env, jclass cls, jlong parentIShellFolder, jlong relativePIDL, jboolean resolve)
{
    HRESULT hres;
    STRRET strret;
    OLECHAR olePath[MAX_PATH]; // wide-char version of path name
    LPWSTR wstr;

    IShellFolder* pParent = (IShellFolder*)parentIShellFolder;
    if (pParent == NULL) {
        return NULL;
    }

    LPITEMIDLIST pidl = (LPITEMIDLIST)relativePIDL;
    if (pidl == NULL) {
        return NULL;
    }

    pParent->GetDisplayNameOf(pidl, SHGDN_NORMAL | SHGDN_FORPARSING, &strret);

    switch (strret.uType) {
      case STRRET_CSTR :
        // IShellFolder::ParseDisplayName requires the path name in Unicode.
        MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, strret.cStr, -1, olePath, MAX_PATH);
        wstr = olePath;
        break;

      case STRRET_OFFSET :
        MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, (CHAR *)pidl + strret.uOffset, -1, olePath, MAX_PATH);
        wstr = olePath;
        break;

      case STRRET_WSTR :
        wstr = strret.pOleStr;
        break;
    }

    BOOL doCoUninit;
    if (!CoInit(doCoUninit)) {
        return 0;
    }
    if (IS_NT) {
        IShellLinkW* psl;
        hres = ::CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, IID_IShellLinkW, (LPVOID *)&psl);
        if (SUCCEEDED(hres)) {
            IPersistFile* ppf;
            hres = psl->QueryInterface(IID_IPersistFile, (void**)&ppf);
            if (SUCCEEDED(hres)) {
                hres = ppf->Load(wstr, STGM_READ);
                if (SUCCEEDED(hres)) {
                    if (resolve) {
                        hres = psl->Resolve(NULL, 0);
                        // Ignore failure
                    }
                    pidl = (LPITEMIDLIST)NULL;
                    hres = psl->GetIDList(&pidl);
                }
                ppf->Release();
            }
            psl->Release();
        }
    } else {
        IShellLinkA* psl;
        hres = ::CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER, IID_IShellLinkA, (LPVOID *)&psl);
        if (SUCCEEDED(hres)) {
            IPersistFile* ppf;
            hres = psl->QueryInterface(IID_IPersistFile, (void**)&ppf);
            if (SUCCEEDED(hres)) {
                hres = ppf->Load(wstr, STGM_READ);
                if (SUCCEEDED(hres)) {
                    if (resolve) {
                        hres = psl->Resolve(NULL, 0);
                        // Ignore failure
                    }
                    pidl = (LPITEMIDLIST)NULL;
                    hres = psl->GetIDList(&pidl);
                }
                ppf->Release();
            }
            psl->Release();
        }
    }
    if (doCoUninit) {
        ::CoUninitialize();
    }

    if (SUCCEEDED(hres)) {
        return (jlong)pidl;
    } else {
        return 0;
    }
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    parseDisplayName0
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_parseDisplayName0
    (JNIEnv* env, jclass cls, jlong jpIShellFolder, jstring jname)
{

    // Get desktop IShellFolder interface
    IShellFolder* pIShellFolder = (IShellFolder*)jpIShellFolder;
    if (pIShellFolder == NULL) {
        JNU_ThrowInternalError(env, "Desktop shell folder missing");
        return 0;
    }
    // Get relative PIDL for name
    LPITEMIDLIST pIDL;
    int nLength = env->GetStringLength(jname);
    jchar* wszPath = new jchar[nLength + 1];
    const jchar* strPath = env->GetStringChars(jname, NULL);
    wcsncpy(wszPath, strPath, nLength);
    wszPath[nLength] = 0;
    HRESULT res = pIShellFolder->ParseDisplayName(NULL, NULL,
                        const_cast<jchar*>(wszPath), NULL, &pIDL, NULL);
    if (res != S_OK) {
        JNU_ThrowIOException(env, "Could not parse name");
        pIDL = 0;
    }
    delete[] wszPath;
    env->ReleaseStringChars(jname, strPath);
    return (jlong)pIDL;
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getDisplayNameOf
 * Signature: (JJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_shell_Win32ShellFolder2_getDisplayNameOf
    (JNIEnv* env, jclass cls, jlong parentIShellFolder, jlong relativePIDL, jint attrs)
{
    IShellFolder* pParent = (IShellFolder*)parentIShellFolder;
    if (pParent == NULL) {
        return NULL;
    }
    LPITEMIDLIST pidl = (LPITEMIDLIST)relativePIDL;
    if (pidl == NULL) {
        return NULL;
    }
    STRRET strret;
    if (pParent->GetDisplayNameOf(pidl, attrs, &strret) != S_OK) {
        return NULL;
    }
    return jstringFromSTRRET(env, pidl, &strret);
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getFolderType
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_shell_Win32ShellFolder2_getFolderType
    (JNIEnv* env, jclass cls, jlong pIDL)
{
    SHFILEINFO fileInfo;
    if (fn_SHGetFileInfo((LPCTSTR)pIDL, 0L, &fileInfo, sizeof(fileInfo),
        SHGFI_TYPENAME | SHGFI_PIDL) == 0) {
        return NULL;
    }
    return JNU_NewStringPlatform(env, fileInfo.szTypeName);
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getExecutableType
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_awt_shell_Win32ShellFolder2_getExecutableType
    (JNIEnv* env, jobject folder, jstring path)
{
    TCHAR szBuf[MAX_PATH];
    LPCTSTR szPath = (LPCTSTR)JNU_GetStringPlatformChars(env, path, NULL);
    if (szPath == NULL) {
        return NULL;
    }
    HINSTANCE res = fn_FindExecutable(szPath, szPath, szBuf);
    JNU_ReleaseStringPlatformChars(env, path, szPath);
    if ((UINT_PTR)res < 32) {
        return NULL;
    }
    return JNU_NewStringPlatform(env, szBuf);
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getIcon
 * Signature: (Ljava/lang/String;Z)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getIcon
    (JNIEnv* env, jclass cls, jstring absolutePath, jboolean getLargeIcon)
{
    HICON hIcon = NULL;
    SHFILEINFO fileInfo;
    LPCTSTR pathStr = (LPCTSTR)JNU_GetStringPlatformChars(env, absolutePath, NULL);
    if (fn_SHGetFileInfo(pathStr, 0L, &fileInfo, sizeof(fileInfo),
                         SHGFI_ICON | (getLargeIcon ? 0 : SHGFI_SMALLICON)) != 0) {
        hIcon = fileInfo.hIcon;
    }
    JNU_ReleaseStringPlatformChars(env, absolutePath, pathStr);
    return (jlong)hIcon;
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getIconIndex
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_shell_Win32ShellFolder2_getIconIndex
    (JNIEnv* env, jclass cls, jlong pIShellIconL, jlong relativePIDL)
{
    IShellIcon* pIShellIcon = (IShellIcon*)pIShellIconL;
    LPITEMIDLIST pidl = (LPITEMIDLIST)relativePIDL;
    if (pIShellIcon == NULL && pidl == NULL) {
        return 0;
    }

    INT index = -1;
    BOOL doCoUninit;
    if (!CoInit(doCoUninit)) {
        return (jint)index;
    }

    HRESULT hres;
    // http://msdn.microsoft.com/library/en-us/shellcc/platform/Shell/programmersguide/shell_int/shell_int_programming/std_ifaces.asp
    if (pIShellIcon != NULL) {
        hres = pIShellIcon->GetIconOf(pidl, GIL_FORSHELL, &index);
    }

    if (doCoUninit) {
        ::CoUninitialize();
    }
    return (jint)index;
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    extractIcon
 * Signature: (JJZ)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_extractIcon
    (JNIEnv* env, jclass cls, jlong pIShellFolderL, jlong relativePIDL, jboolean getLargeIcon)
{
    IShellFolder* pIShellFolder = (IShellFolder*)pIShellFolderL;
    LPITEMIDLIST pidl = (LPITEMIDLIST)relativePIDL;
    if (pIShellFolder == NULL || pidl == NULL) {
        return 0;
    }

    HICON hIcon = NULL;
    BOOL doCoUninit;
    if (!CoInit(doCoUninit)) {
        return (jlong)hIcon;
    }

    HRESULT hres;
    if (IS_NT) {
        IExtractIconW* pIcon;
        hres = pIShellFolder->GetUIObjectOf(NULL, 1, const_cast<LPCITEMIDLIST*>(&pidl),
                                        IID_IExtractIconW, NULL, (void**)&pIcon);
        if (SUCCEEDED(hres)) {
            WCHAR szBuf[MAX_PATH];
            INT index;
            UINT flags;
            hres = pIcon->GetIconLocation(GIL_FORSHELL, szBuf, MAX_PATH, &index, &flags);
            if (SUCCEEDED(hres)) {
                HICON hIconLarge;
                hres = pIcon->Extract(szBuf, index, &hIconLarge, &hIcon, (16 << 16) + 32);
                if (SUCCEEDED(hres)) {
                    if (getLargeIcon) {
                        fn_DestroyIcon((HICON)hIcon);
                        hIcon = hIconLarge;
                    } else {
                        fn_DestroyIcon((HICON)hIconLarge);
                    }
                }
            }
            pIcon->Release();
        }
    } else {
        IExtractIconA* pIcon;
        hres = pIShellFolder->GetUIObjectOf(NULL, 1, const_cast<LPCITEMIDLIST*>(&pidl),
                                        IID_IExtractIconA, NULL, (void**)&pIcon);
        if (SUCCEEDED(hres)) {
            CHAR szBuf[MAX_PATH];
            INT index;
            UINT flags;
            hres = pIcon->GetIconLocation(GIL_FORSHELL, szBuf, MAX_PATH, &index, &flags);
            if (SUCCEEDED(hres)) {
                HICON hIconLarge;
                hres = pIcon->Extract(szBuf, index, &hIconLarge, &hIcon, (16 << 16) + 32);
                if (SUCCEEDED(hres)) {
                    if (getLargeIcon) {
                        fn_DestroyIcon((HICON)hIcon);
                        hIcon = hIconLarge;
                    } else {
                        fn_DestroyIcon((HICON)hIconLarge);
                    }
                }
            }
            pIcon->Release();
        }
    }
    if (doCoUninit) {
        ::CoUninitialize();
    }
    return (jlong)hIcon;
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    disposeIcon
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_shell_Win32ShellFolder2_disposeIcon
    (JNIEnv* env, jclass cls, jlong hicon)
{
    fn_DestroyIcon((HICON)hicon);
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getIconBits
 * Signature: (JI)[I
 */
JNIEXPORT jintArray JNICALL Java_sun_awt_shell_Win32ShellFolder2_getIconBits
    (JNIEnv* env, jclass cls, jlong hicon, jint iconSize)
{
    jintArray iconBits = NULL;

    // Get the icon info
    ICONINFO iconInfo;
    if (fn_GetIconInfo((HICON)hicon, &iconInfo)) {
        // Get the screen DC
        HDC dc = GetDC(NULL);
        if (dc != NULL) {
            // Set up BITMAPINFO
            BITMAPINFO bmi;
            memset(&bmi, 0, sizeof(BITMAPINFO));
            bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
            bmi.bmiHeader.biWidth = iconSize;
            bmi.bmiHeader.biHeight = -iconSize;
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = BI_RGB;
            // Extract the color bitmap
            int nBits = iconSize * iconSize;
            long colorBits[1024];
            GetDIBits(dc, iconInfo.hbmColor, 0, iconSize, colorBits, &bmi, DIB_RGB_COLORS);
            // XP supports alpha in some icons, and depending on device.
            // This should take precedence over the icon mask bits.
            BOOL hasAlpha = FALSE;
            if (isXP) {
                for (int i = 0; i < nBits; i++) {
                    if ((colorBits[i] & 0xff000000) != 0) {
                        hasAlpha = TRUE;
                        break;
                    }
                }
            }
            if (!hasAlpha) {
                // Extract the mask bitmap
                long maskBits[1024];
                GetDIBits(dc, iconInfo.hbmMask, 0, iconSize, maskBits, &bmi, DIB_RGB_COLORS);
                // Copy the mask alphas into the color bits
                for (int i = 0; i < nBits; i++) {
                    if (maskBits[i] == 0) {
                        colorBits[i] |= 0xff000000;
                    }
                }
            }
            // Release DC
            ReleaseDC(NULL, dc);
            // Create java array
            iconBits = env->NewIntArray(nBits);
            // Copy values to java array
            env->SetIntArrayRegion(iconBits, 0, nBits, colorBits);
        }
        // Fix 4745575 GDI Resource Leak
        // MSDN
        // GetIconInfo creates bitmaps for the hbmMask and hbmColor members of ICONINFO.
        // The calling application must manage these bitmaps and delete them when they
        // are no longer necessary.
        ::DeleteObject(iconInfo.hbmColor);
        ::DeleteObject(iconInfo.hbmMask);
    }
    return iconBits;
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getFileChooserBitmapBits
 * Signature: ()[I
 */
JNIEXPORT jintArray JNICALL Java_sun_awt_shell_Win32ShellFolder2_getFileChooserBitmapBits
    (JNIEnv* env, jclass cls)
{
    HBITMAP hBitmap = NULL;
    BITMAP bm;
    HINSTANCE libComCtl32;
    HINSTANCE libShell32;


    libShell32 = LoadLibrary(TEXT("shell32.dll"));
    if (libShell32 != NULL) {
        long osVersion = GetVersion();
        BOOL isVista = (!(osVersion & 0x80000000) && (LOBYTE(LOWORD(osVersion)) >= 6));

        hBitmap = (HBITMAP)LoadImage(libShell32,
                    isVista ? TEXT("IDB_TB_SH_DEF_16") : MAKEINTRESOURCE(216),
                    IMAGE_BITMAP, 0, 0, LR_CREATEDIBSECTION);
    }
    if (hBitmap == NULL) {
        libComCtl32 = LoadLibrary(TEXT("comctl32.dll"));
        if (libComCtl32 != NULL) {
            hBitmap = (HBITMAP)LoadImage(libComCtl32, MAKEINTRESOURCE(124),
                                         IMAGE_BITMAP, 0, 0, LR_CREATEDIBSECTION);
        }
    }
    if (hBitmap == NULL) {
        return NULL;
    }

    GetObject(hBitmap, sizeof(bm), (LPSTR)&bm);

    // Get the screen DC
    HDC dc = GetDC(NULL);
    if (dc == NULL) {
        return NULL;
    }

    // Set up BITMAPINFO
    BITMAPINFO bmi;
    memset(&bmi, 0, sizeof(BITMAPINFO));
    bmi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth = bm.bmWidth;
    bmi.bmiHeader.biHeight = -bm.bmHeight;
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = BI_RGB;
    // Extract the color bitmap
    int numPixels = bm.bmWidth * bm.bmHeight;
    //long colorBits[192 * 16];
    long *colorBits = (long*)safe_Malloc(numPixels * sizeof(long));
    if (GetDIBits(dc, hBitmap, 0, bm.bmHeight, colorBits, &bmi, DIB_RGB_COLORS) == 0) {
        return NULL;
    }

    // Release DC
    ReleaseDC(NULL, dc);

    // The color of the first pixel defines the transparency, according
    // to the documentation for LR_LOADTRANSPARENT at
    // http://msdn.microsoft.com/library/psdk/winui/resource_9fhi.htm
    long transparent = colorBits[0];
    for (int i=0; i < numPixels; i++) {
        if (colorBits[i] != transparent) {
            colorBits[i] |= 0xff000000;
        }
    }

    // Create java array
    jintArray bits = env->NewIntArray(numPixels);
    // Copy values to java array
    env->SetIntArrayRegion(bits, 0, numPixels, colorBits);
    // Fix 4745575 GDI Resource Leak
    ::DeleteObject(hBitmap);
    ::FreeLibrary(libComCtl32);
    return bits;
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getSystemIcon
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getSystemIcon
    (JNIEnv* env, jclass cls, jint iconID)
{
    return (jlong)LoadIcon(NULL, MAKEINTRESOURCE(iconID));
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    getIconResource
 * Signature: (Ljava/lang/String;IIIZ)J
 */
JNIEXPORT jlong JNICALL Java_sun_awt_shell_Win32ShellFolder2_getIconResource
    (JNIEnv* env, jclass cls, jstring libName, jint iconID,
     jint cxDesired, jint cyDesired, jboolean useVGAColors)
{
    HINSTANCE libHandle = LoadLibrary(env->GetStringChars(libName, NULL));
    if (libHandle != NULL) {
        UINT fuLoad = (useVGAColors && !isXP) ? LR_VGACOLOR : 0;
        return ptr_to_jlong(LoadImage(libHandle, MAKEINTRESOURCE(iconID),
                                      IMAGE_ICON, cxDesired, cyDesired,
                                      fuLoad));
    }
    return 0;
}


// Helper functions for workaround COM initialization:

static HRESULT GetDetailsOfFolder(
        IShellFolder2 *folder,
        LPCITEMIDLIST pidl,
        UINT column,
        SHELLDETAILS *psd)
{
    HRESULT hr = folder->GetDetailsOf(pidl, column, psd);
    if (IS_WINVISTA && FAILED (hr)) {
        BOOL doCoUninit;
        if (CoInit(doCoUninit)) {
            hr = folder->GetDetailsOf(pidl, column, psd);
            if (doCoUninit) {
                ::CoUninitialize();
            }
        }
    }
    return hr;
}

static HRESULT GetDetailsOf(
        IShellDetails *details,
        LPCITEMIDLIST pidl,
        UINT column,
        SHELLDETAILS *psd)
{
    HRESULT hr = details->GetDetailsOf(pidl, column, psd);
    if (IS_WINVISTA && FAILED (hr)) {
        BOOL doCoUninit;
        if (CoInit(doCoUninit)) {
            hr = details->GetDetailsOf(pidl, column, psd);
            if (doCoUninit) {
                ::CoUninitialize();
            }
        }
    }
    return hr;
}

/*
 * Helper function for creating Java column info object
 */
static jobject CreateColumnInfo(JNIEnv *pEnv,
                                jclass *pClass, jmethodID *pConstructor,
                                SHELLDETAILS *psd, ULONG visible)
{
    return pEnv->NewObject(*pClass, *pConstructor,
                    jstringFromSTRRET(pEnv, NULL, &(psd->str)),
                    (jint)(psd->cxChar * 6), // TODO: is 6 OK for converting chars to pixels?
                    (jint)psd->fmt, (jboolean) visible);
}


/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    doGetColumnInfo
 * Signature: (J)[Lsun/awt/shell/ShellFolderColumnInfo;
 */
JNIEXPORT jobjectArray JNICALL
    Java_sun_awt_shell_Win32ShellFolder2_doGetColumnInfo
            (JNIEnv *env, jobject obj, jlong iShellFolder)
{

    HRESULT hr;
    IShellFolder *pIShellFolder = (IShellFolder*) iShellFolder;
    IUnknown *pIUnknown = NULL;

    jclass columnClass = env->FindClass("sun/awt/shell/ShellFolderColumnInfo");
    if(NULL == columnClass) {
        return NULL;
    }

    jmethodID columnConstructor =
        env->GetMethodID(columnClass, "<init>", "(Ljava/lang/String;IIZ)V");
    if(NULL == columnConstructor) {
        return NULL;
    }

    // We'are asking the object the list of available columns
    SHELLDETAILS sd;

    hr = pIShellFolder->QueryInterface(IID_IShellFolder2, (void**)&pIUnknown);
    if(SUCCEEDED (hr)) {

        // The folder exposes IShellFolder2 interface
        IShellFolder2 *pIShellFolder2 = (IShellFolder2*) pIUnknown;

        // Count columns
        int colNum = -1;
        hr = S_OK;
        do{
            hr = GetDetailsOfFolder(pIShellFolder2, NULL, ++colNum, &sd);
        } while (SUCCEEDED (hr));

        jobjectArray columns =
            env->NewObjectArray((jsize) colNum, columnClass, NULL);
        if(NULL == columns) {
            pIShellFolder2->Release();
            return NULL;
        }

        // Fill column details list
        SHCOLSTATEF csFlags;
        colNum = 0;
        hr = S_OK;
        while (SUCCEEDED (hr)) {
            hr = GetDetailsOfFolder(pIShellFolder2, NULL, colNum, &sd);

            if (SUCCEEDED (hr)) {
                hr = pIShellFolder2->GetDefaultColumnState(colNum, &csFlags);
                if (SUCCEEDED (hr)) {
                    if(!(csFlags & SHCOLSTATE_HIDDEN)) {
                        jobject column = CreateColumnInfo(env,
                                            &columnClass, &columnConstructor,
                                            &sd, csFlags & SHCOLSTATE_ONBYDEFAULT);
                        env->SetObjectArrayElement(columns, (jsize) colNum, column);
                    }
                }
                colNum++;
            }
        }

        pIShellFolder2->Release();

        return columns;
    }

    hr = pIShellFolder->CreateViewObject(NULL, IID_IShellDetails, (void**)&pIUnknown);
    if(SUCCEEDED (hr)) {
        // The folder exposes IShellDetails interface
        IShellDetails *pIShellDetails = (IShellDetails*) pIUnknown;

        // Count columns
        int colNum = -1;
        hr = S_OK;
        do{
            hr = GetDetailsOf(pIShellDetails, NULL, ++colNum, &sd);
        } while (SUCCEEDED (hr));

        jobjectArray columns =
            env->NewObjectArray((jsize) colNum, columnClass, NULL);
        if(NULL == columns) {
            pIShellDetails->Release();
            return NULL;
        }

        // Fill column details list
        colNum = 0;
        hr = S_OK;
        while (SUCCEEDED (hr)) {
            hr = GetDetailsOf(pIShellDetails, NULL, colNum, &sd);
            if (SUCCEEDED (hr)) {
                jobject column = CreateColumnInfo(env,
                                    &columnClass, &columnConstructor,
                                    &sd, 1);
                env->SetObjectArrayElement(columns, (jsize) colNum++, column);
            }
        }

        pIShellDetails->Release();

        return columns;
    }

    // The folder exposes neither IShellFolder2 nor IShelDetails
    return NULL;

}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    doGetColumnValue
 * Signature: (JJI)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
    Java_sun_awt_shell_Win32ShellFolder2_doGetColumnValue
            (JNIEnv *env, jobject obj, jlong iShellFolder,
            jlong jpidl, jint columnIdx)
{

    HRESULT hr;
    IShellFolder *pIShellFolder = (IShellFolder*) iShellFolder;
    IUnknown *pIUnknown = NULL;


    LPITEMIDLIST pidl = (LPITEMIDLIST) jpidl;
    SHELLDETAILS sd;

    hr = pIShellFolder->QueryInterface(IID_IShellFolder2, (void**)&pIUnknown);
    if(SUCCEEDED (hr)) {
        // The folder exposes IShellFolder2 interface
        IShellFolder2 *pIShellFolder2 = (IShellFolder2*) pIUnknown;
        hr = GetDetailsOfFolder(pIShellFolder2, pidl, (UINT)columnIdx, &sd);
        pIShellFolder2->Release();
        if (SUCCEEDED (hr)) {
            STRRET strRet = sd.str;
            return jstringFromSTRRET(env, pidl, &strRet);
        }
    }

    hr = pIShellFolder->CreateViewObject(NULL, IID_IShellDetails, (void**)&pIUnknown);
    if(SUCCEEDED (hr)) {
        // The folder exposes IShellDetails interface
        IShellDetails *pIShellDetails = (IShellDetails*) pIUnknown;
        hr = GetDetailsOf(pIShellDetails, pidl, (UINT)columnIdx, &sd);
        pIShellDetails->Release();
        if (SUCCEEDED (hr)) {
            STRRET strRet = sd.str;
            return jstringFromSTRRET(env, pidl, &strRet);
        }
    }

    // The folder exposes neither IShellFolder2 nor IShelDetails
    return NULL;
}

/*
 * Class:     sun_awt_shell_Win32ShellFolder2
 * Method:    compareIDsByColumn
 * Signature: (JJJI)I
 */
JNIEXPORT jint JNICALL
    Java_sun_awt_shell_Win32ShellFolder2_compareIDsByColumn
            (JNIEnv* env, jclass cls, jlong jpParentIShellFolder,
            jlong pIDL1, jlong pIDL2, jint columnIdx)
{
    IShellFolder* pParentIShellFolder = (IShellFolder*)jpParentIShellFolder;
    if (pParentIShellFolder == NULL) {
        return 0;
    }

    HRESULT hr = pParentIShellFolder->CompareIDs(
                                            (UINT) columnIdx,
                                            (LPCITEMIDLIST) pIDL1,
                                            (LPCITEMIDLIST) pIDL2);
    if (SUCCEEDED (hr)) {
        return (jint) (short) HRESULT_CODE(hr);
    }

    return 0;
}


} // extern "C"
