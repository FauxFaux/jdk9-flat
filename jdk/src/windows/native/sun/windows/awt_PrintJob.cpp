/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <math.h>
#include <windef.h>
#include <wtypes.h>
#include <winuser.h>
#include <commdlg.h>
#include <winspool.h>

#include "awt.h"
#include "awt_dlls.h"
#include "awt_Toolkit.h"
#include "awt_Component.h"
#include "awt_Font.h"
#include "awt_PrintDialog.h"
#include "awt_PrintControl.h"
#include "awt_Window.h"
#include "ComCtl32Util.h"

#include <sun_awt_windows_WPrinterJob.h>
#include <jlong_md.h>
#include <float.h>

#define DEBUG_PRINTING  0

/* Round 'num' to the nearest integer and return
 * the result as a long.
 */
#define ROUND_TO_LONG(num)    ((long) floor((num) + 0.5))

/* Round 'num' to the nearest integer and return
 * the result as an int.
 */
#define ROUND_TO_INT(num)     ((int) floor((num) + 0.5))

/************************************************************************
 * WPrintJob native methods
 */

extern "C" {

/*** Private Constants ***/

static char *kJavaIntStr = "I";
static char *kJavaLongStr = "J";

/* 2D printing uses 3 byte BGR pixels in Raster printing */
static int J2DRasterBPP = 3;

/*
 * Class Names
 */
static const char *PRINTEREXCEPTION_STR = "java/awt/print/PrinterException";

/*
 * The following strings are the names of instance variables in WPrintJob2D.
 */
static const char *PRINTPAPERSIZE_STR = "mPrintPaperSize"; // The paper size
static const char *XRES_STR = "mPrintXRes";     // The x dpi.
static const char *YRES_STR = "mPrintYRes";     // The y dpi.
static const char *PHYSX_STR = "mPrintPhysX";   // pixel x of printable area
static const char *PHYSY_STR = "mPrintPhysY";   // pixel y of printable area
static const char *PHYSW_STR = "mPrintWidth";   // pixel wid of printable area
static const char *PHYSH_STR = "mPrintHeight";  // pixel hgt of printable area
static const char *PAGEW_STR = "mPageWidth";    // pixel wid of page
static const char *PAGEH_STR = "mPageHeight";   // pixel hgt of page

static const char *DRIVER_COPIES_STR = "driverDoesMultipleCopies";
static const char *DRIVER_COLLATE_STR = "driverDoesCollation";
static const char *USER_COLLATE_STR = "userRequestedCollation";
static const char *NO_DEFAULTPRINTER_STR = "noDefaultPrinter";
static const char *LANDSCAPE_270_STR = "landscapeRotates270";


// public int java.awt.print.PrinterJob.getCopies()

static const char *GETCOPIES_STR = "getCopies";
static const char *GETCOPIES_SIG = "()I";

/*
 * Methods and fields in awt.print.PageFormat.
 */

// public Paper getPaper()
static const char *GETPAPER_STR = "getPaper";
static const char *GETPAPER_SIG = "()Ljava/awt/print/Paper;";

// public void setPaper(Paper paper)
static const char *SETPAPER_STR = "setPaper";
static const char *SETPAPER_SIG = "(Ljava/awt/print/Paper;)V";

// public int getOrientation()
static const char *GETORIENT_STR = "getOrientation";
static const char *GETORIENT_SIG = "()I";

// public void setOrientation(int orientation)
static const char *SETORIENT_STR = "setOrientation";
static const char *SETORIENT_SIG = "(I)V";

static const int PAGEFORMAT_LANDSCAPE = 0;
static const int PAGEFORMAT_PORTRAIT = 1;
//static const int PAGEFORMAT_REVERSELANDSCAPE = 2;

// instance variables for PrintRequestAttribute settings
static const char *ATTSIDES_STR = "mAttSides";
static const char *ATTCHROMATICITY_STR = "mAttChromaticity";
static const char *ATTXRES_STR = "mAttXRes";
static const char *ATTYRES_STR = "mAttYRes";
static const char *ATTQUALITY_STR = "mAttQuality";
static const char *ATTCOLLATE_STR = "mAttCollate";
static const char *ATTCOPIES_STR = "mAttCopies";
static const char *ATTMEDIASZNAME_STR = "mAttMediaSizeName";
static const char *ATTMEDIATRAY_STR = "mAttMediaTray";

/*
 * Methods in awt.print.Paper.
 */

// public void setSize(double width, double height)
static const char *SETSIZE_STR = "setSize";
static const char *SETSIZE_SIG = "(DD)V";

// protected void setImageableArea(double x, double y, double width,
//                                                  double height)
static const char *SETIMAGEABLE_STR = "setImageableArea";
static const char *SETIMAGEABLE_SIG = "(DDDD)V";

// public double getWidth()
static const char *GETWIDTH_STR = "getWidth";
static const char *GETWIDTH_SIG = "()D";

// public double getHeight()
static const char *GETHEIGHT_STR = "getHeight";
static const char *GETHEIGHT_SIG = "()D";

// public double getImageableX()
static const char *GETIMG_X_STR = "getImageableX";
static const char *GETIMG_X_SIG = "()D";

// public double getImageableY()
static const char *GETIMG_Y_STR = "getImageableY";
static const char *GETIMG_Y_SIG = "()D";

// public double getImageableWidth()
static const char *GETIMG_W_STR = "getImageableWidth";
static const char *GETIMG_W_SIG = "()D";

// public double getImageableHeight()
static const char *GETIMG_H_STR = "getImageableHeight";
static const char *GETIMG_H_SIG = "()D";

/* Multiply a Window's MM_HIENGLISH value
 * (1000th of an inch) by this number to
 * get a value in 72nds of an inch.
 */
static const double HIENGLISH_TO_POINTS = (72.0 / 1000.0);

/* Multiply a Window's MM_HIMETRIC value
 * (100ths of a millimeter) by this
 * number to get a value in 72nds of an inch.
 */
static const double HIMETRIC_TO_POINTS = (72.0 / 2540.0);

/* Multiply a Window's MM_LOMETRIC value
 * (10ths of a millimeter) by this
 * number to get a value in 72nds of an inch.
 */
static const double LOMETRIC_TO_POINTS = (72.0 / 254.0);

/* Multiply a measurement in 1/72's of an inch by this
 * value to convert it to Window's MM_HIENGLISH
 * (1000th of an inch) units.
 */
static const double POINTS_TO_HIENGLISH = (1000.0 / 72.0);

/* Multiply a measurement in 1/72's of an inch by this
 * value to convert it to Window's MM_HIMETRIC
 * (100th of an millimeter) units.
 */
static const double POINTS_TO_HIMETRIC = (2540.0 / 72.0);

/* Multiply a measurement in 1/72's of an inch by this
 * value to convert it to Window's MM_LOMETRIC
 * (10th of an millimeter) units.
 */
static const double POINTS_TO_LOMETRIC = (254.0 / 72.0);

jfieldID AwtPrintDialog::pageID;


/*** Private Macros ***/

/* A Page Setup paint hook passes a word describing the
   orientation and type of page being displayed in the
   dialog. These macros break the word down into meaningful
   values.
*/
#define PRINTER_TYPE_MASK   (0x0003)
#define PORTRAIT_MASK       (0x0004)
#define ENVELOPE_MASK       (0x0008)

#define IS_ENVELOPE(param)  (((param) & ENVELOPE_MASK) != 0)
#define IS_PORTRAIT(param)  (((param) & PORTRAIT_MASK) != 0)

/*      If the Pagable does not know the number of pages in the document,
        then we limit the print dialog to this number of pages.
*/
#define MAX_UNKNOWN_PAGES 9999

/* When making a font that is already at least bold,
 * bolder then we increase the LOGFONT lfWeight field
 * by this amount.
 */
#define EMBOLDEN_WEIGHT   (100)

/* The lfWeight field of a GDI LOGFONT structure should not
 * exceed this value.
 */
#define MAX_FONT_WEIGHT   (1000)

/*** Private Variable Types ***/

typedef struct {
    jdouble x;
    jdouble y;
    jdouble width;
    jdouble height;
} RectDouble;

/*** Private Prototypes ***/

static UINT CALLBACK pageDlgHook(HWND hDlg, UINT msg,
                                 WPARAM wParam, LPARAM lParam);
static void initPrinter(JNIEnv *env, jobject self);
static HDC getDefaultPrinterDC(JNIEnv *env, jobject printerJob);
static void pageFormatToSetup(JNIEnv *env, jobject job, jobject page,
                              PAGESETUPDLG *setup, HDC hDC);
static WORD getOrientationFromDevMode2(HGLOBAL hDevMode);
static WORD getOrientationFromDevMode(JNIEnv *env, jobject self);
static void setOrientationInDevMode(HGLOBAL hDevMode, jboolean isPortrait);
static void doPrintBand(JNIEnv *env, jboolean browserPrinting,
                        HDC printDC, jbyteArray imageArray,
                        jint x, jint y, jint width, jint height);
static int bitsToDevice(HDC printDC, jbyte *image, long destX, long destY,
                        long width, long height);
static void retrievePaperInfo(const PAGESETUPDLG *setup, POINT *paperSize,
                              RECT *margins, jint *orientation,
                              HDC hdc);
static jint getCopies(JNIEnv *env, jobject printerJob);
static jobject getPaper(JNIEnv *env, jobject page);
static void setPaper(JNIEnv *env, jobject page, jobject paper);
static jint getPageFormatOrientation(JNIEnv *env, jobject page);
static void setPageFormatOrientation(JNIEnv *env, jobject page, jint orient);
static void getPaperValues(JNIEnv *env, jobject paper, RectDouble *paperSize,
                          RectDouble *margins, BOOL widthAsMargin=TRUE);
static void setPaperValues(JNIEnv *env, jobject paper, const POINT *paperSize,
                            const RECT *margins, int units);
static long convertFromPoints(double value, int units);
static double convertToPoints(long value, int units);
void setCapabilities(JNIEnv *env, jobject self, HDC printDC);
static inline WORD getPrintPaperSize(JNIEnv *env, jobject self);
static inline void setPrintPaperSize(JNIEnv *env, jobject self, WORD sz);
static jint getIntField(JNIEnv *env, jobject self, const char *fieldName);
static jlong getLongField(JNIEnv *env, jobject self, const char *fieldName);
static void setIntField(JNIEnv *env, jobject self,
                            const char *fieldName, jint value);
static void setLongField(JNIEnv *env, jobject self,
                            const char *fieldName, jlong value);
static jfieldID getIdOfIntField(JNIEnv *env, jobject self,
                            const char *fieldName);
static jfieldID getIdOfLongField(JNIEnv *env, jobject self,
                            const char *fieldName);
static void setBooleanField(JNIEnv *env, jobject self,
                            const char *fieldName, jboolean value);

static jbyte *findNonWhite(jbyte *image, long sy, long width, long height,
                           long scanLineStride, long *numLinesP);
static jbyte *findWhite(jbyte *image, long sy, long width, long height,
                           long scanLineStride, long *numLines);
static void dumpDevMode(HGLOBAL hDevMode);
static void dumpPrinterCaps(HANDLE hDevNames);
static void throwPrinterException(JNIEnv *env, DWORD err);
static void matchPaperSize(HDC printDC, HGLOBAL hDevMode, HGLOBAL hDevNames,
                           double origWid, double origHgt,
                           double* newHgt, double *newWid,
                           WORD* paperSize);

/***********************************************************************/

static jboolean jFontToWFontW(JNIEnv *env, HDC printDC, jstring fontName,
                        jfloat fontSize, jboolean isBold, jboolean isItalic,
                        jint rotation, jfloat awScale);
static jboolean jFontToWFontA(JNIEnv *env, HDC printDC, jstring fontName,
                        jfloat fontSize, jboolean isBold, jboolean isItalic,
                        jint rotation, jfloat awScale);

static int CALLBACK fontEnumProcW(ENUMLOGFONTEXW  *lpelfe,
                                 NEWTEXTMETRICEX *lpntme,
                                 int FontType,
                                 LPARAM lParam);
static int CALLBACK fontEnumProcA(ENUMLOGFONTEXA  *logfont,
                                  NEWTEXTMETRICEX  *lpntme,
                                  int FontType,
                                  LPARAM lParam);

static int embolden(int currentWeight);
static BOOL getPrintableArea(HDC pdc, HANDLE hDevMode, RectDouble *margin);

/************************************************************************
 * WPageDialog native methods
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPageDialog_initIDs(JNIEnv *env, jclass cls)
{
    TRY;

    AwtPrintDialog::pageID =
        env->GetFieldID(cls, "page", "Ljava/awt/print/PageFormat;");

    DASSERT(AwtPrintDialog::pageID != NULL);

    CATCH_BAD_ALLOC;
}

/************************************************************************
 * WPageDialogPeer native methods
 */

/*
 * Class:     sun_awt_windows_WPageDialogPeer
 * Method:    show
 * Signature: ()Z
 *
 */

JNIEXPORT jboolean JNICALL
Java_sun_awt_windows_WPageDialogPeer__1show(JNIEnv *env, jobject peer)
{
    TRY;

    // as peer object is used later on another thread, create global ref here
    jobject peerGlobalRef = env->NewGlobalRef(peer);
    DASSERT(peerGlobalRef != NULL);
    jobject target = env->GetObjectField(peerGlobalRef, AwtObject::targetID);

    jobject parent = env->GetObjectField(peerGlobalRef, AwtPrintDialog::parentID);

    jobject page = env->GetObjectField(target, AwtPrintDialog::pageID);
    DASSERT(page != NULL);

    jobject self = env->GetObjectField(target, AwtPrintDialog::controlID);
    DASSERT(self != NULL);

    AwtComponent *awtParent = (parent != NULL) ? (AwtComponent *)JNI_GET_PDATA(parent) : NULL;
    HWND hwndOwner = awtParent ? awtParent->GetHWnd() : NULL;

    jboolean doIt = JNI_FALSE; // Assume the user will cancel the dialog.
    PAGESETUPDLG setup;
    memset(&setup, 0, sizeof(setup));

    setup.lStructSize = sizeof(setup);

    /*
      Fix for 6488834.
      To disable Win32 native parent modality we have to set
      hwndOwner field to either NULL or some hidden window. For
      parentless dialogs we use NULL to show them in the taskbar,
      and for all other dialogs AwtToolkit's HWND is used.
    */
    if (awtParent != NULL)
    {
        setup.hwndOwner = AwtToolkit::GetInstance().GetHWnd();
    }
    else
    {
        setup.hwndOwner = NULL;
    }

    setup.hDevMode = NULL;
    setup.hDevNames = NULL;
    setup.Flags = PSD_RETURNDEFAULT | PSD_DEFAULTMINMARGINS;
    // setup.ptPaperSize =
    // setup.rtMinMargin =
    // setup.rtMargin =
    setup.hInstance = NULL;
    setup.lCustData = (LPARAM)peerGlobalRef;
    setup.lpfnPageSetupHook = reinterpret_cast<LPPAGESETUPHOOK>(pageDlgHook);
    setup.lpfnPagePaintHook = NULL;
    setup.lpPageSetupTemplateName = NULL;
    setup.hPageSetupTemplate = NULL;


    /* Because the return default flag is set, this first call
     * will not display the dialog but will return default values, inc
     * including hDevMode, hDevName, ptPaperSize, and rtMargin values.
     * We can use the devmode to set the orientation of the page
     * and the size of the page.
     * The units used by the user is also needed.
     */
    if (AwtPrintControl::getPrintHDMode(env, self) == NULL ||
        AwtPrintControl::getPrintHDName(env,self) == NULL) {
        (void)AwtCommDialog::PageSetupDlg(&setup);
        /* check if hDevMode and hDevNames are set.
         * If both are null, then there is no default printer.
         */
        if ((setup.hDevMode == NULL) && (setup.hDevNames == NULL)) {
            return JNI_FALSE;
        }
    } else {
        int measure = PSD_INTHOUSANDTHSOFINCHES;
        int sz = GetLocaleInfo(LOCALE_USER_DEFAULT, LOCALE_IMEASURE, NULL, 0);
        if (sz > 0) {
          LPTSTR str = (LPTSTR)safe_Malloc(sizeof(TCHAR) * sz);
          if (str != NULL) {
            sz = GetLocaleInfo(LOCALE_USER_DEFAULT, LOCALE_IMEASURE, str, sz);
            if (sz > 0) {
              if (_tcscmp(TEXT("0"), str) == 0) {
                measure = PSD_INHUNDREDTHSOFMILLIMETERS;
              }
            }
            free((LPTSTR)str);
          }
        }
        setup.Flags |= measure;
        setup.hDevMode = AwtPrintControl::getPrintHDMode(env, self);
        setup.hDevNames = AwtPrintControl::getPrintHDName(env, self);
    }
    /* Move page size and orientation from the PageFormat object
     * into the Windows setup structure so that the format can
     * be displayed in the dialog.
     */
    pageFormatToSetup(env, self, page, &setup,
                      AwtPrintControl::getPrintDC(env, self));

    setup.lpfnPageSetupHook = reinterpret_cast<LPPAGESETUPHOOK>(pageDlgHook);
    setup.Flags = PSD_ENABLEPAGESETUPHOOK | PSD_MARGINS;

    AwtDialog::CheckInstallModalHook();

    BOOL ret = AwtCommDialog::PageSetupDlg(&setup);
    if (ret) {

        jobject paper = getPaper(env, page);

        int units = setup.Flags & PSD_INTHOUSANDTHSOFINCHES ?
                                                MM_HIENGLISH :
                                                MM_HIMETRIC;
        POINT paperSize;
        RECT margins;
        jint orientation;

        /* The printer may have been changed, and we track that change,
         * but then need to get a new DC for the current printer so that
         * we validate the paper size correctly
         */
        if (setup.hDevNames != NULL) {
            DEVNAMES* names = (DEVNAMES*)::GlobalLock(setup.hDevNames);
            if (names != NULL) {
                LPTSTR printer = (LPTSTR)names+names->wDeviceOffset;
                SAVE_CONTROLWORD
                HDC newDC = ::CreateDC(TEXT("WINSPOOL"), printer, NULL, NULL);
                RESTORE_CONTROLWORD
                if (newDC != NULL) {
                    HDC oldDC = AwtPrintControl::getPrintDC(env, self);
                    if (oldDC != NULL) {
                         ::DeleteDC(oldDC);
                    }
                }
                AwtPrintControl::setPrintDC(env, self, newDC);
            }
            ::GlobalUnlock(setup.hDevNames);
        }

        /* Get the Windows paper and margins description.
        */
        retrievePaperInfo(&setup, &paperSize, &margins, &orientation,
                          AwtPrintControl::getPrintDC(env, self));

        /* Convert the Windows' paper and margins description
         * and place them into a Paper instance.
         */
        setPaperValues(env, paper, &paperSize, &margins, units);

        /* Put the updated Paper instance and the orientation into
         * the PageFormat.
         */
        setPaper(env, page, paper);

        setPageFormatOrientation(env, page, orientation);

        if (setup.hDevMode != NULL) {
            DEVMODE *devmode = (DEVMODE *)::GlobalLock(setup.hDevMode);
            if (devmode != NULL) {
                if (devmode->dmFields & DM_PAPERSIZE) {
                    setPrintPaperSize(env, self, devmode->dmPaperSize);
                }
            }
            ::GlobalUnlock(setup.hDevMode);
        }
        doIt = JNI_TRUE;
    }

    DASSERT(env->GetLongField(peer, AwtComponent::hwndID) == 0L);

    AwtDialog::CheckUninstallModalHook();

    AwtDialog::ModalActivateNextWindow(NULL, target, peer);

    HGLOBAL oldG = AwtPrintControl::getPrintHDMode(env, self);
    if (setup.hDevMode != oldG) {
        AwtPrintControl::setPrintHDMode(env, self, setup.hDevMode);
    }

    oldG = AwtPrintControl::getPrintHDName(env, self);
    if (setup.hDevNames != oldG) {
        AwtPrintControl::setPrintHDName(env, self, setup.hDevNames);
    }

    env->DeleteGlobalRef(peerGlobalRef);
    if (target != NULL) {
        env->DeleteLocalRef(target);
    }
    if (parent != NULL) {
        env->DeleteLocalRef(parent);
    }
    env->DeleteLocalRef(page);
    env->DeleteLocalRef(self);

    return doIt;

    CATCH_BAD_ALLOC_RET(0);
}

/************************************************************************
 * WPrinterJob native methods
 */

/*
 * Class:   sun_awt_windows_WPrinterJob
 * Method:  setCopies
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_setNativeCopies(JNIEnv *env, jobject self,
                                           jint copies) {
    HGLOBAL hDevMode = AwtPrintControl::getPrintHDMode(env, self);
    if (hDevMode != NULL) {
      DEVMODE *devmode = (DEVMODE *)::GlobalLock(hDevMode);
      if (devmode != NULL) {
        short nCopies = (copies < (jint)SHRT_MAX)
          ? static_cast<short>(copies) : SHRT_MAX;
        devmode->dmCopies = nCopies;
        devmode->dmFields |= DM_COPIES;
      }
      ::GlobalUnlock(hDevMode);
    }
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    getDefaultPage
 * Signature: (Ljava/awt/print/PageFormat;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_getDefaultPage(JNIEnv *env, jobject self,
                                                jobject page) {

  TRY;

  // devnames and dc are initialized at setting of Print Service,
  // through print dialog or start of printing
  // None of those may have happened yet, so call initPrinter()
  initPrinter(env, self);
  HANDLE hDevNames = AwtPrintControl::getPrintHDName(env, self);
  HDC hdc = AwtPrintControl::getPrintDC(env, self);

  if ((hDevNames == NULL) || (hdc == NULL)) {
      return;
  }

  DEVNAMES *devnames = (DEVNAMES *)::GlobalLock(hDevNames);

  if (devnames != NULL) {

    LPTSTR lpdevnames = (LPTSTR)devnames;
    LPTSTR printerName = _tcsdup(lpdevnames+devnames->wDeviceOffset);

    HANDLE      hPrinter = NULL;
    LPDEVMODE   pDevMode;

    /* Start by opening the printer */
    if (!::OpenPrinter(printerName, &hPrinter, NULL)) {
      if (hPrinter != NULL) {
        ::ClosePrinter(hPrinter);
      }
      ::GlobalUnlock(hDevNames);
      free ((LPTSTR) printerName);
      return;
    }

    if (!AwtPrintControl::getDevmode(hPrinter, printerName, &pDevMode)) {
        /* if failure, cleanup and return failure */
        if (pDevMode != NULL) {
            ::GlobalFree(pDevMode);
        }
        ::ClosePrinter(hPrinter);
        ::GlobalUnlock(hDevNames);
        free ((LPTSTR) printerName);
        return ;
    }

    if (pDevMode->dmFields & DM_PAPERSIZE) {
        POINT paperSize;
        RECT margins;
        jint orientation = PAGEFORMAT_PORTRAIT;

        if (hdc != NULL) {

          int units = MM_HIENGLISH;
          int sz = GetLocaleInfo(LOCALE_USER_DEFAULT,
                                 LOCALE_IMEASURE, NULL, 0);
          if (sz > 0) {
            LPTSTR str = (LPTSTR)safe_Malloc(sizeof(TCHAR) * sz);
            if (str != NULL) {
              sz = GetLocaleInfo(LOCALE_USER_DEFAULT,
                                 LOCALE_IMEASURE, str, sz);
              if (sz > 0) {
                if (_tcscmp(TEXT("0"), str) == 0) {
                  units = MM_HIMETRIC;
                }
              }
              free((LPTSTR)str);
            }
          }

          int width = ::GetDeviceCaps(hdc, PHYSICALWIDTH);
          int height = ::GetDeviceCaps(hdc, PHYSICALHEIGHT);
          int resx = ::GetDeviceCaps(hdc, LOGPIXELSX);
          int resy = ::GetDeviceCaps(hdc, LOGPIXELSY);

          double w = (double)width/resx;
          double h = (double)height/resy;

          paperSize.x = convertFromPoints(w*72, units);
          paperSize.y = convertFromPoints(h*72, units);

          // set margins to 1"
          margins.left = convertFromPoints(72, units);
          margins.top = convertFromPoints(72, units);;
          margins.right = convertFromPoints(72, units);;
          margins.bottom = convertFromPoints(72, units);;

          jobject paper = getPaper(env, page);
          setPaperValues(env, paper, &paperSize, &margins, units);
          setPaper(env, page, paper);

          if ((pDevMode->dmFields & DM_ORIENTATION) &&
              (pDevMode->dmOrientation == DMORIENT_LANDSCAPE)) {
              orientation = PAGEFORMAT_LANDSCAPE;
          }
          setPageFormatOrientation(env, page, orientation);
        }

    } else {
        setBooleanField(env, self, NO_DEFAULTPRINTER_STR, (jint)JNI_TRUE);
    }
    ::GlobalFree(pDevMode);

    free ((LPTSTR) printerName);

    ::ClosePrinter(hPrinter);

  }
  ::GlobalUnlock(hDevNames);

  CATCH_BAD_ALLOC;

}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    validatePaper
 * Signature: (Ljava/awt/print/Paper;Ljava/awt/print/Paper;)V
 *
 * Query the current or default printer to find all paper sizes it
 * supports and find the closest matching to the origPaper.
 * For the matching size, validate the margins and printable area
 * against the printer's capabilities.
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_validatePaper(JNIEnv *env, jobject self,
                                         jobject origPaper, jobject newPaper) {
    TRY;

    /* If the print dialog has been displayed or a DC has otherwise
     * been created, use that. Else get a DC for the default printer
     * which we discard before returning.
     */

    HDC printDC = AwtPrintControl::getPrintDC(env, self);
    HGLOBAL hDevMode = AwtPrintControl::getPrintHDMode(env, self);
    HGLOBAL hDevNames = AwtPrintControl::getPrintHDName(env, self);
    BOOL privateDC = FALSE;

    if (printDC == NULL) {
        PRINTDLG pd;
        memset(&pd, 0, sizeof(PRINTDLG));
        pd.lStructSize = sizeof(PRINTDLG);
        pd.Flags = PD_RETURNDEFAULT | PD_RETURNDC;

        if (AwtCommDialog::PrintDlg(&pd)) {
            printDC = pd.hDC;
            hDevMode = pd.hDevMode;
            hDevNames = pd.hDevNames;
            privateDC = TRUE;
        }
    }

    if (printDC == NULL) {
       return;
    }

    /* We try to mitigate the effects of floating point rounding errors
     * by only setting a value if it would differ from the value in the
     * target by at least 0.10 points = 1/720 inches.
     * eg if the values present in the target are close to the calculated
     * values then we accept the target.
     */
    const double epsilon = 0.10;

    jdouble paperWidth, paperHeight;
    WORD dmPaperSize = getPrintPaperSize(env, self);

    double ix, iy, iw, ih, pw, ph;

    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());
    jmethodID getID;

    jclass paperClass = env->GetObjectClass(origPaper);
    getID = env->GetMethodID(paperClass, GETWIDTH_STR, GETWIDTH_SIG);
    pw = env->CallDoubleMethod(origPaper, getID);
    getID = env->GetMethodID(paperClass, GETHEIGHT_STR, GETHEIGHT_SIG);
    ph = env->CallDoubleMethod(origPaper, getID);
    getID = env->GetMethodID(paperClass, GETIMG_X_STR, GETIMG_X_SIG);
    ix = env->CallDoubleMethod(origPaper, getID);
    getID = env->GetMethodID(paperClass, GETIMG_Y_STR, GETIMG_Y_SIG);
    iy = env->CallDoubleMethod(origPaper, getID);
    getID = env->GetMethodID(paperClass, GETIMG_W_STR, GETIMG_W_SIG);
    iw = env->CallDoubleMethod(origPaper, getID);
    getID = env->GetMethodID(paperClass, GETIMG_H_STR, GETIMG_H_SIG);
    ih = env->CallDoubleMethod(origPaper, getID);

    matchPaperSize(printDC, hDevMode, hDevNames, pw, ph,
                   &paperWidth, &paperHeight, &dmPaperSize);

    /* Validate margins and imageable area */

    // pixels per inch in x and y direction
    jint xPixelRes = GetDeviceCaps(printDC, LOGPIXELSX);
    jint yPixelRes = GetDeviceCaps(printDC, LOGPIXELSY);

    // x & y coord of printable area in pixels
    jint xPixelOrg = GetDeviceCaps(printDC, PHYSICALOFFSETX);
    jint yPixelOrg = GetDeviceCaps(printDC, PHYSICALOFFSETY);

    // width & height of printable area in pixels
    jint imgPixelWid = GetDeviceCaps(printDC, HORZRES);
    jint imgPixelHgt = GetDeviceCaps(printDC, VERTRES);

    // if the values were obtained from a rotated device, swap.
    if (getOrientationFromDevMode2(hDevMode) == DMORIENT_LANDSCAPE) {
      jint tmp;
      tmp = xPixelRes;
      xPixelRes = yPixelRes;
      yPixelRes = tmp;

      tmp = xPixelOrg;
      xPixelOrg = yPixelOrg;
      yPixelOrg = tmp;

      tmp = imgPixelWid;
      imgPixelWid = imgPixelHgt;
      imgPixelHgt = tmp;
    }

    // page imageable area in 1/72"
    jdouble imgX = (jdouble)((xPixelOrg * 72)/(jdouble)xPixelRes);
    jdouble imgY = (jdouble)((yPixelOrg * 72)/(jdouble)yPixelRes);
    jdouble imgWid = (jdouble)((imgPixelWid * 72)/(jdouble)xPixelRes);
    jdouble imgHgt = (jdouble)((imgPixelHgt * 72)/(jdouble)yPixelRes);

    /* Check each of the individual values is within range.
     * Then make sure imageable area is placed within imageable area.
     * Allow for a small floating point error in the comparisons
     */

    if (ix < 0.0 ) {
        ix = 0.0;
    }
    if (iy < 0.0 ) {
        iy = 0.0;
    }
    if (iw < 0.0) {
        iw = 0.0;
    }
    if (ih < 0.0) {
        ih = 0.0;
    }
    if ((ix + epsilon) < imgX) {
         ix = imgX;
    }
    if ((iy + epsilon) < imgY) {
         iy = imgY;
    }
    if (iw + epsilon > imgWid) {
        iw = imgWid;
    }
    if (ih + epsilon > imgHgt) {
        ih = imgHgt;
    }
    if ((ix + iw + epsilon) > (imgX+imgWid)) {
        ix = (imgX+imgWid) - iw;
    }
    if ((iy + ih + epsilon) > (imgY+imgHgt)) {
        iy = (imgY+imgHgt) - ih;
    }

    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());

    jmethodID setSizeID = env->GetMethodID(paperClass,
                                        SETSIZE_STR, SETSIZE_SIG);
    jmethodID setImageableID = env->GetMethodID(paperClass,
                                        SETIMAGEABLE_STR, SETIMAGEABLE_SIG);

    env->CallVoidMethod(newPaper, setSizeID, paperWidth, paperHeight);
    env->CallVoidMethod(newPaper, setImageableID, ix, iy, iw, ih);

    /* Free any resources allocated */
    if (privateDC == TRUE) {
        if (printDC != NULL) {
            /* In this case we know that this DC has no GDI objects to free */
             ::DeleteDC(printDC);
        }
        if (hDevMode != NULL) {
            ::GlobalFree(hDevMode);
        }
        if (hDevNames != NULL) {
            ::GlobalFree(hDevNames);
        }
    }

    CATCH_BAD_ALLOC;
}

static void initPrinter(JNIEnv *env, jobject self) {

    HDC printDC = AwtPrintControl::getPrintDC(env, self);

    /*
     * The print device context will be NULL if the
     * user never okayed a print dialog. This
     * will happen most often when the java application
     * decides not to present a print dialog to the user.
     * We create a device context for the default printer.
     */
    if (printDC == NULL) {
        printDC = getDefaultPrinterDC(env, self);
        if (printDC){
            AwtPrintControl::setPrintDC(env, self, printDC);
            setCapabilities(env, self, printDC);
        }
    }
}


/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    initPrinter
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_initPrinter(JNIEnv *env, jobject self) {
    TRY;

    initPrinter(env, self);

    // check for collation
    HGLOBAL hDevNames = AwtPrintControl::getPrintHDName(env, self);
    if (hDevNames != NULL) {
        DWORD dmFields;
        DEVNAMES *devnames = (DEVNAMES *)::GlobalLock(hDevNames);

        if (devnames != NULL) {
            LPTSTR lpdevnames = (LPTSTR)devnames;
            LPTSTR printername = lpdevnames+devnames->wDeviceOffset;
            LPTSTR port = lpdevnames+devnames->wOutputOffset;

            SAVE_CONTROLWORD
            dmFields = ::DeviceCapabilities(printername, port,
                                            DC_FIELDS, NULL, NULL);
            int devLandRotation = (int)DeviceCapabilities(printername, port,
                                        DC_ORIENTATION, NULL, NULL);
            RESTORE_CONTROLWORD
            ::GlobalUnlock(devnames);

            if (devLandRotation == 270) {
              setBooleanField(env, self, LANDSCAPE_270_STR, JNI_TRUE);
            } else {
              setBooleanField(env, self, LANDSCAPE_270_STR, JNI_FALSE);
            }
        }

        if (dmFields & DM_COLLATE) {
            setBooleanField(env, self, DRIVER_COLLATE_STR, JNI_TRUE);
        } else {
            setBooleanField(env, self, DRIVER_COLLATE_STR, JNI_FALSE);
        }

    }

    CATCH_BAD_ALLOC;
}


static bool setPrintReqAttribute(JNIEnv *env, jobject self, DEVMODE* devmode) {

    /* The xRes/yRes fields are only initialised if there is a resolution
     * attribute. Otherwise they both will be zero, in which case default
     * resolution should be fine. Consider calling getXRes()/getResY()
     * rather than accessing the fields directly
     */
    int xRes=getIntField(env, self, ATTXRES_STR);
    int yRes=getIntField(env, self, ATTYRES_STR);
    int quality=getIntField(env, self, ATTQUALITY_STR);
    int printColor = getIntField(env, self, ATTCHROMATICITY_STR);
    int sides = getIntField(env, self, ATTSIDES_STR);
    int collate = getIntField(env, self, ATTCOLLATE_STR);
    int copies = 1;
    jclass myClass = env->GetObjectClass(self);
    // There may be cases when driver reports it cannot handle
    // multiple copies although it actually can .  So this modification
    // handles that, to make sure that we report copies = 1 because
    // we already emulated multiple copies.
    jfieldID fieldId = env->GetFieldID(myClass, DRIVER_COPIES_STR, "Z");
    if (env->GetBooleanField(self, fieldId)) {
      copies = getIntField(env, self, ATTCOPIES_STR);
    } // else "driverDoesMultipleCopies" is false, copies should be 1 (default)
    int mediatray = getIntField(env, self, ATTMEDIATRAY_STR);
    int mediaszname = getIntField(env, self, ATTMEDIASZNAME_STR);
    bool ret = true;

    if (quality && quality < 0) {
        if (quality != devmode->dmPrintQuality) {
            devmode->dmPrintQuality = quality;
            devmode->dmFields |= DM_PRINTQUALITY;
            // ret of "false" means that setCapabilities needs to be called
            ret = false;
        }
    } else {
        /* If we didn't set quality, maybe we have resolution settings. */
        if (xRes && (xRes != devmode->dmPrintQuality)) {
            devmode->dmPrintQuality = xRes;
            devmode->dmFields |= DM_PRINTQUALITY;
        }

        if (yRes && (yRes != devmode->dmYResolution)) {
            devmode->dmYResolution = yRes;
            devmode->dmFields |= DM_YRESOLUTION;
        }
    }

    if (printColor && (printColor != devmode->dmColor)) {
        devmode->dmColor = printColor;
        devmode->dmFields |= DM_COLOR;
    }

    if (sides && (sides != devmode->dmDuplex)) {
        devmode->dmDuplex = sides;
        devmode->dmFields |= DM_DUPLEX;
    }

    if ((collate != -1) && (collate != devmode->dmCollate)) {
        devmode->dmCollate = collate;
        devmode->dmFields |= DM_COLLATE;
    }

    if (copies && (copies != devmode->dmCopies)) {
        devmode->dmCopies = copies;
        devmode->dmFields |= DM_COPIES;
    }

    if (mediatray && (mediatray != devmode->dmDefaultSource)) {
        devmode->dmDefaultSource = mediatray;
        devmode->dmFields |= DM_DEFAULTSOURCE;
    }

    if (mediaszname && (mediaszname != devmode->dmPaperSize)) {
        devmode->dmPaperSize = mediaszname;
        devmode->dmFields |= DM_PAPERSIZE;
    }

    return ret;
}

static LPTSTR GetPrinterPort(JNIEnv *env, LPTSTR printer) {

  HANDLE hPrinter;
  if (::OpenPrinter(printer, &hPrinter, NULL) == FALSE) {
      return NULL;
  }

  DWORD bytesReturned, bytesNeeded;
  ::GetPrinter(hPrinter, 2, NULL, 0, &bytesNeeded);
  PRINTER_INFO_2* info2 = (PRINTER_INFO_2*)::GlobalAlloc(GPTR, bytesNeeded);
  if (info2 == NULL) {
      ::ClosePrinter(hPrinter);
      return NULL;
  }

  int ret = ::GetPrinter(hPrinter, 2, (LPBYTE)info2,
                         bytesNeeded, &bytesReturned);
  ::ClosePrinter(hPrinter);
  if (!ret) {
    ::GlobalFree(info2);
    return NULL;
  }

  LPTSTR port = wcsdup(info2->pPortName);
  ::GlobalFree(info2);
  return port;
}

static jboolean isFilePort(LPTSTR port) {
    return wcscmp(port, TEXT("FILE:")) == 0;
}

/*
 * This is called when printing is about to start and we have not specified
 * a file destination - which is in fact the 99.99% case.
 * We can discover from the DEVNAMES if the DC is actually associated
 * with "FILE:", which is going to occur
 * 1) if the native print dialog was used and print to file was selected, or
 * 2) the printer driver is configured to print to file.
 * In that former case we have a conflict since if the destination is a
 * file, JDK will normally supply that destination to StartDoc, so what
 * must have happened is the app de-associated the job from the file, but
 * the printer DC etc is still hooked up to the file. If we find
 * the DEVNAMES specified is set to "FILE:"
 * First find out if the DC was associated with a FILE. If it is,
 * then unless that is its normal configuration, we'll get a new DC.
 * If the default destination ends with ":", this is sufficient clue
 * to windows it must be a device. Otherwise we need to create a new DC.
 */
LPTSTR VerifyDestination(JNIEnv *env, jobject wPrinterJob) {

    LPTSTR dest = NULL;
    HDC printDC = AwtPrintControl::getPrintDC(env, wPrinterJob);
    HGLOBAL hDevNames = AwtPrintControl::getPrintHDName(env, wPrinterJob);
    if (hDevNames == NULL || printDC == NULL) {
        return NULL;
    }

    DEVNAMES *devnames = (DEVNAMES *)::GlobalLock(hDevNames);
    if (devnames != NULL) {
        LPTSTR lpdevnames = (LPTSTR)devnames;
        LPTSTR printer = lpdevnames+devnames->wDeviceOffset;
        LPTSTR port = lpdevnames+devnames->wOutputOffset;
        if (port != NULL && isFilePort(port)) {
            LPTSTR defPort = GetPrinterPort(env, printer);
            if (!isFilePort(defPort)) { // not a FILE: port by default
                int len = wcslen(defPort);
                if (len > 0 && port[len-1] == L':') { // is a device port
                    dest = defPort;
                } else {
                    /* We need to create a new DC */
                    HDC newDC = ::CreateDC(TEXT("WINSPOOL"),
                                           printer, NULL, NULL);
                    AwtPrintControl::setPrintDC(env, wPrinterJob, newDC);
                    DeleteDC(printDC);
                }
            }
            if (dest != defPort) {
                free(defPort);
            }
        }
        ::GlobalUnlock(hDevNames);
    }
    return dest;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    startDoc
 * Signature: ()V
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_windows_WPrinterJob__1startDoc(JNIEnv *env, jobject self,
                                            jstring dest, jstring jobname) {
    TRY;

    int err = 0;

    LPTSTR destination = NULL;
    if (dest != NULL) {
        destination = (LPTSTR)JNU_GetStringPlatformChars(env, dest, NULL);
    } else {
        destination = VerifyDestination(env, self);
    }
    LPTSTR docname = NULL;
    if (jobname != NULL) {
        LPTSTR tmp = (LPTSTR)JNU_GetStringPlatformChars(env, jobname, NULL);
        docname = _tcsdup(tmp);
        JNU_ReleaseStringPlatformChars(env, jobname, tmp);
    } else {
        docname = TEXT("Java Printing");
    }

    initPrinter(env, self);
    HDC printDC = AwtPrintControl::getPrintDC(env, self);

    SAVE_CONTROLWORD
    /* We do our own rotation, so device must be in portrait mode.
     * This should be in effect only whilst we are printing, so that
     * if the app displays the native dialog again for the same printerjob
     * instance, it shows the setting the user expects.
     * So in EndDoc, and AbortDoc or if we fail out of this function,
     * we need to restore this.
     */
    HGLOBAL hDevMode = AwtPrintControl::getPrintHDMode(env, self);
    if (printDC != NULL && hDevMode != NULL) {
        DEVMODE *devmode = (DEVMODE *)::GlobalLock(hDevMode);
        if (devmode != NULL) {
                devmode->dmFields |= DM_ORIENTATION;
                devmode->dmOrientation = DMORIENT_PORTRAIT;
                /* set attribute values into devmode */
                bool ret = setPrintReqAttribute(env, self, devmode);
                ::ResetDC(printDC, devmode);
                RESTORE_CONTROLWORD

                if (!ret) {
                    /*
                      Need to read in updated device capabilities because
                      print quality has been changed.
                    */
                    setCapabilities(env, self, printDC);
                }
        }
        ::GlobalUnlock(hDevMode);
    }

    if (printDC){
        DOCINFO docInfo;
        memset(&docInfo, 0, sizeof(DOCINFO));
        docInfo.cbSize = sizeof (DOCINFO);
        docInfo.lpszDocName = docname;

        TCHAR fullPath[_MAX_PATH];
        if (destination != NULL) {
            _tfullpath(fullPath, destination, _MAX_PATH);
            docInfo.lpszOutput = fullPath;
        }

        docInfo.fwType = 0;

        err = ::StartDoc(printDC, &docInfo);
        RESTORE_CONTROLWORD
        free((void*)docInfo.lpszDocName);
        if (err <= 0) {
            err = GetLastError();
        } else {
            err = 0;
        }
        if (dest != NULL) {
            JNU_ReleaseStringPlatformChars(env, dest, destination);
        }
    }
    else {
        jclass printerException = env->FindClass(PRINTEREXCEPTION_STR);
        env->ThrowNew(printerException, "No printer found.");
    }

    if (err && err != ERROR_CANCELLED) {
        throwPrinterException(env, err);
    }
    if (err == ERROR_CANCELLED) {
        return JNI_FALSE;
    } else {
        return JNI_TRUE;
    }

    CATCH_BAD_ALLOC_RET(0);
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    endDoc
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_endDoc(JNIEnv *env, jobject self) {
    TRY;

    HDC printDC = AwtPrintControl::getPrintDC(env, self);

    if (printDC != NULL){
        SAVE_CONTROLWORD
        ::EndDoc(printDC);
        RESTORE_CONTROLWORD
    }

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    abortDoc
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_abortDoc(JNIEnv *env, jobject self) {
    TRY;

    HDC printDC = AwtPrintControl::getPrintDC(env, self);

    if (printDC != NULL){
         ::AbortDoc(printDC);
    }

    CATCH_BAD_ALLOC;
}

static void DeletePrintDC(HDC printDC) {
    if (printDC==NULL) {
        return;
    }
    /* Free any GDI objects we may have selected into the DC.
     * It is not harmful to call DeleteObject if the retrieved objects
     * happen to be stock objects.
     */
    HBRUSH hbrush = (HBRUSH)::SelectObject(printDC,
                                           ::GetStockObject(BLACK_BRUSH));
    if (hbrush != NULL) {
        ::DeleteObject(hbrush);
    }
    HPEN hpen = (HPEN)::SelectObject(printDC, ::GetStockObject(BLACK_PEN));
    if (hpen != NULL) {
        ::DeleteObject(hpen);
    }
    HFONT hfont = (HFONT)::SelectObject(printDC,::GetStockObject(SYSTEM_FONT));
    if (hfont != NULL) {
        ::DeleteObject(hfont);
    }
    ::DeleteDC(printDC);
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    deleteDC
 * Signature: ()V
 * Called after WPrinterJob has been GCed, not before.
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_deleteDC
(JNIEnv *env, jclass wpjClass, jlong dc, jlong devmode, jlong devnames) {

    TRY_NO_VERIFY;

    DeletePrintDC((HDC)dc);

    if ((HGLOBAL)devmode != NULL){
         ::GlobalFree((HGLOBAL)devmode);
    }
    if ((HGLOBAL)devnames != NULL){
         ::GlobalFree((HGLOBAL)devnames);
    }

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    deviceStartPage
 * Signature: (Ljava/awt/print/PageFormat;Ljava/awt/print/Printable;I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_deviceStartPage
(JNIEnv *env, jobject self, jobject format, jobject painter, jint pageIndex,
 jboolean pageChanged) {
    TRY;

    HDC printDC = AwtPrintControl::getPrintDC(env, self);

    if (printDC != NULL){
        LONG retval = 0;
        HGLOBAL hDevMode = AwtPrintControl::getPrintHDMode(env, self);
        HGLOBAL hDevNames = AwtPrintControl::getPrintHDName(env, self);
        WORD dmPaperSize = getPrintPaperSize(env, self);
        SAVE_CONTROLWORD
          // Unless the PageFormat has been changed, do not set the paper
          // size for a new page. Doing so is unnecessary, perhaps expensive,
          // and can lead some printers to emit the paper prematurely in
          // duplex mode.
        if (hDevMode != NULL && hDevNames != NULL && pageChanged) {

            RectDouble paperSize;
            RectDouble margins;
            jobject paper = getPaper(env, format);
            getPaperValues(env, paper, &paperSize, &margins);
            double paperWidth, paperHeight;
            matchPaperSize(printDC, hDevMode, hDevNames,
                           paperSize.width,  paperSize.height,
                           &paperWidth, &paperHeight, &dmPaperSize);

            DEVMODE *devmode = (DEVMODE *)::GlobalLock(hDevMode);
            if (devmode != NULL) {
                if (dmPaperSize == 0) {
                  devmode->dmFields |= DM_PAPERLENGTH | DM_PAPERWIDTH
                    | DM_PAPERSIZE;
                  devmode->dmPaperSize = DMPAPER_USER;
                  devmode->dmPaperWidth =
                    (short)(convertFromPoints(paperSize.width, MM_LOMETRIC));
                  devmode->dmPaperLength =
                    (short)(convertFromPoints(paperSize.height, MM_LOMETRIC));
                  // sync with public devmode settings
                  {
                    DEVNAMES *devnames = (DEVNAMES *)::GlobalLock(hDevNames);
                    if (devnames != NULL) {

                      LPTSTR lpdevnames = (LPTSTR)devnames;
                      LPTSTR printerName = _tcsdup(lpdevnames+devnames->wDeviceOffset);

                      HANDLE hPrinter;
                      if (::OpenPrinter(printerName, &hPrinter, NULL)== TRUE) {

                        // Need to call DocumentProperties to update change
                        // in paper setting because some drivers do not update
                        // it with a simple call to ResetDC.
                        retval = ::DocumentProperties(NULL, hPrinter,printerName,
                                             devmode, devmode,
                                             DM_IN_BUFFER|DM_OUT_BUFFER);
                        RESTORE_CONTROLWORD

                        ::ClosePrinter(hPrinter);
                        free ((char*)printerName);
                      }
                    }

                    ::GlobalUnlock(hDevNames);
                  } // sync
                  HDC res = ::ResetDC(printDC, devmode);
                  RESTORE_CONTROLWORD
                }  // if (dmPaperSize == 0)
                // if DocumentProperties() fail
               if (retval < 0) {
                  ::GlobalUnlock(hDevMode);
                  return;
               }
            }
            ::GlobalUnlock(hDevMode);
        }

        ::StartPage(printDC);
        RESTORE_CONTROLWORD

        /* The origin for a glyph will be along the left
         * edge of its bnounding box at the base line.
         * The coincides with the Java text glyph origin.
         */
        ::SetTextAlign(printDC, TA_LEFT | TA_BASELINE);

        /* The background mode is used when GDI draws text,
         * hatched brushes and poen that are not solid.
         * We set the mode to transparentso that when
         * drawing text only the glyphs themselves are
         * drawn. The boundingbox of the string is not
         * erased to the background color.
         */
        ::SetBkMode(printDC, TRANSPARENT);
    }

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    deviceEndPage
 * Signature: (Ljava/awt/print/PageFormat;Ljava/awt/print/Printable;I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_deviceEndPage
(JNIEnv *env, jobject self, jobject format, jobject painter, jint pageIndex) {
    TRY;

    HDC printDC = AwtPrintControl::getPrintDC(env, self);

    if (printDC != NULL){
        SAVE_CONTROLWORD
        ::EndPage(printDC);
        RESTORE_CONTROLWORD
    }

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WEmbeddedFrame
 * Method:    isPrinterDC
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_windows_WEmbeddedFrame_isPrinterDC
    (JNIEnv *env, jobject self, jlong hdc) {

    HDC realHDC = (HDC)hdc;
    if (realHDC == NULL) {
        return JNI_FALSE;
    }

    int technology = GetDeviceCaps(realHDC, TECHNOLOGY);
#if DEBUG_PRINTING
     FILE *file = fopen("c:\\plog.txt", "a");
     fprintf(file,"tech is %d\n", technology);
     fclose(file);
#endif //DEBUG_PRINTING
    switch (GetDeviceCaps(realHDC, TECHNOLOGY)) {
    case DT_RASPRINTER :
        return JNI_TRUE;
    case DT_RASDISPLAY :
    case DT_METAFILE   :
        if (GetObjectType(realHDC) == OBJ_ENHMETADC) {
            return JNI_TRUE;
        }
    default : return JNI_FALSE;
    }
}

/*
 * Class:     sun_awt_windows_WEmbeddedFrame
 * Method:    printBand
 * Signature: (J[BIIIIIIIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WEmbeddedFrame_printBand
  (JNIEnv *env, jobject self, jlong theHDC, jbyteArray imageArray,
   jint offset, jint srcX,  jint srcY,  jint srcWidth,  jint srcHeight,
   jint destX, jint destY, jint destWidth, jint destHeight) {

    if (theHDC == NULL || imageArray == NULL ||
        srcWidth <= 0 || srcHeight == 0 || destWidth == 0 || destHeight <=0) {
        return;
    }

    HDC hDC = (HDC)theHDC;

    /* The code below is commented out until its proven necessary. In its
     * original form of PatBlit(hDC, destX,destY,destWidth, destHeight ..)
     * it resulted in the PS driver showing a white fringe, perhaps because
     * the PS driver enclosed the specified area rather than filling its
     * interior. The code is believed to have been there to prevent such
     * artefacts rather than cause them. This may have been related to
     * the earlier implementation using findNonWhite(..) and breaking the
     * image blit up into multiple blit calls. This currently looks as if
     * its unnecessary as the driver performs adequate compression where
     * such all white spans exist
     */
//     HGDIOBJ oldBrush =
//      ::SelectObject(hDC, AwtBrush::Get(RGB(0xff, 0xff, 0xff))->GetHandle());
//     ::PatBlt(hDC, destX+1, destY+1, destWidth-2, destHeight-2, PATCOPY);
//     ::SelectObject(hDC, oldBrush);

    TRY;
    jbyte *image = NULL;
    try {
        image = (jbyte *)env->GetPrimitiveArrayCritical(imageArray, 0);
        struct {
            BITMAPINFOHEADER bmiHeader;
            DWORD*                 bmiColors;
        } bitMapHeader;

        memset(&bitMapHeader,0,sizeof(bitMapHeader));
        bitMapHeader.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
        bitMapHeader.bmiHeader.biWidth = srcWidth;
        bitMapHeader.bmiHeader.biHeight = srcHeight;
        bitMapHeader.bmiHeader.biPlanes = 1;
        bitMapHeader.bmiHeader.biBitCount = 24;
        bitMapHeader.bmiHeader.biCompression = BI_RGB;

        int result =
            ::StretchDIBits(hDC,
                            destX,         // left of dest rect
                            destY,         // top of dest rect
                            destWidth,     // width of dest rect
                            destHeight,    // height of dest rect
                            srcX,          // left of source rect
                            srcY,          // top of source rect
                            srcWidth,      // number of 1st source scan line
                            srcHeight,     // number of source scan lines
                            image+offset,  // points to the DIB
                            (BITMAPINFO *)&bitMapHeader,
                            DIB_RGB_COLORS,
                            SRCCOPY);
#if DEBUG_PRINTING
     FILE *file = fopen("c:\\plog.txt", "a");
     fprintf(file,"sh=%d dh=%d sy=%d dy=%d result=%d\n", srcHeight, destHeight, srcY, destY, result);
     fclose(file);
#endif //DEBUG_PRINTING
    } catch (...) {
        if (image != NULL) {
            env->ReleasePrimitiveArrayCritical(imageArray, image, 0);
        }
        throw;
    }

    env->ReleasePrimitiveArrayCritical(imageArray, image, 0);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    printBand
 * Signature: ([BIIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_printBand
  (JNIEnv *env, jobject self, jbyteArray imageArray, jint x, jint y,
   jint width, jint height) {

    HDC printDC = AwtPrintControl::getPrintDC(env, self);
    doPrintBand(env, JNI_FALSE, printDC, imageArray, x, y, width, height);
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    beginPath
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_beginPath
(JNIEnv *env , jobject self, jlong printDC) {
    TRY;

    (void) ::BeginPath((HDC)printDC);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    endPath
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_endPath
(JNIEnv *env, jobject self, jlong printDC) {
    TRY;

    (void) ::EndPath((HDC)printDC);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    fillPath
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_fillPath
(JNIEnv *env, jobject self, jlong printDC) {
    TRY;

    (void) ::FillPath((HDC)printDC);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    closeFigure
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_closeFigure
(JNIEnv *env, jobject self, jlong printDC) {
    TRY;

    (void) ::CloseFigure((HDC)printDC);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    lineTo
 * Signature: (JFF)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_lineTo
(JNIEnv *env, jobject self, jlong printDC, jfloat x, jfloat y) {
    TRY;

    (void) ::LineTo((HDC)printDC, ROUND_TO_LONG(x), ROUND_TO_LONG(y));

    CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    moveTo
 * Signature: (JFF)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_moveTo
(JNIEnv *env, jobject self, jlong printDC, jfloat x, jfloat y) {
    TRY;

    (void) ::MoveToEx((HDC)printDC, ROUND_TO_LONG(x), ROUND_TO_LONG(y), NULL);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    polyBezierTo
 * Signature: (JFFFFFF)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_polyBezierTo
(JNIEnv *env, jobject self, jlong printDC,
 jfloat control1x, jfloat control1y,
 jfloat control2x, jfloat control2y,
 jfloat endX, jfloat endY) {

    TRY;

    POINT points[3];

    points[0].x = ROUND_TO_LONG(control1x);
    points[0].y = ROUND_TO_LONG(control1y);
    points[1].x = ROUND_TO_LONG(control2x);
    points[1].y = ROUND_TO_LONG(control2y);
    points[2].x = ROUND_TO_LONG(endX);
    points[2].y = ROUND_TO_LONG(endY);

    (void) ::PolyBezierTo((HDC)printDC, points, 3);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    setPolyFillMode
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_setPolyFillMode
(JNIEnv *env, jobject self, jlong printDC, jint fillRule) {
    TRY;

    (void) ::SetPolyFillMode((HDC)printDC, fillRule);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    selectSolidBrush
 * Signature: (JIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_selectSolidBrush
(JNIEnv *env, jobject self, jlong printDC, jint red, jint green, jint blue) {

    TRY;

    HBRUSH colorBrush = ::CreateSolidBrush(RGB(red, green, blue));
    HBRUSH oldBrush = (HBRUSH)::SelectObject((HDC)printDC, colorBrush);
    DeleteObject(oldBrush);

    CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    getPenX
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_windows_WPrinterJob_getPenX
(JNIEnv *env, jobject self, jlong printDC) {

    TRY;

    POINT where;
    ::GetCurrentPositionEx((HDC)printDC, &where);

    return (jint) where.x;

    CATCH_BAD_ALLOC_RET(0);
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    getPenY
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_windows_WPrinterJob_getPenY
(JNIEnv *env, jobject self, jlong printDC) {

    TRY;

    POINT where;
    ::GetCurrentPositionEx((HDC)printDC, &where);

    return (jint) where.y;

    CATCH_BAD_ALLOC_RET(0);
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    selectClipPath
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_selectClipPath
(JNIEnv *env, jobject self, jlong printDC) {

    TRY;

    ::SelectClipPath((HDC)printDC, RGN_COPY);

    CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    frameRect
 * Signature: (JFFFF)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_frameRect
(JNIEnv *env, jobject self, jlong printDC,
 jfloat x, jfloat y, jfloat width, jfloat height) {

  TRY;

  POINT points[5];

  points[0].x = ROUND_TO_LONG(x);
  points[0].y = ROUND_TO_LONG(y);
  points[1].x = ROUND_TO_LONG(x+width);
  points[1].y = ROUND_TO_LONG(y);
  points[2].x = ROUND_TO_LONG(x+width);
  points[2].y = ROUND_TO_LONG(y+height);
  points[3].x = ROUND_TO_LONG(x);
  points[3].y = ROUND_TO_LONG(y+height);
  points[4].x = ROUND_TO_LONG(x);
  points[4].y = ROUND_TO_LONG(y);

  ::Polyline((HDC)printDC, points, sizeof(points)/sizeof(points[0]));

  CATCH_BAD_ALLOC;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    fillRect
 * Signature: (JFFFFIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_fillRect
(JNIEnv *env, jobject self, jlong printDC,
 jfloat x, jfloat y, jfloat width, jfloat height,
 jint red, jint green, jint blue) {

  TRY;

  RECT rect;
  rect.left = ROUND_TO_LONG(x);
  rect.top = ROUND_TO_LONG(y);
  rect.right = ROUND_TO_LONG(x+width);
  rect.bottom = ROUND_TO_LONG(y+height);

  HBRUSH brush = ::CreateSolidBrush(RGB(red, green, blue));

  if (brush != NULL) {
    ::FillRect((HDC)printDC, (LPRECT) &rect, brush);
    DeleteObject(brush);
  }

  CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    selectPen
 * Signature: (JFIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_selectPen
(JNIEnv *env, jobject self, jlong printDC, jfloat width,
 jint red, jint green, jint blue) {

  TRY;

  HPEN hpen =  ::CreatePen(PS_SOLID, ROUND_TO_LONG(width),
                           RGB(red, green, blue));

  if (hpen != NULL) {
    HPEN oldpen = (HPEN) ::SelectObject((HDC)printDC, hpen);

    if (oldpen != NULL) {
      DeleteObject(oldpen);
    }
  }

  CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    selectStylePen
 * Signature: (JJJFIII)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_windows_WPrinterJob_selectStylePen
(JNIEnv *env, jobject self, jlong printDC, jlong cap, jlong join, jfloat width,
 jint red, jint green, jint blue) {

  /* End cap and line join styles are not supported in Win 9x. */
  if (IS_WIN95)
    return JNI_FALSE;

  TRY;

  LOGBRUSH logBrush;

  logBrush.lbStyle = PS_SOLID ;
  logBrush.lbColor = RGB(red, green, blue);
  logBrush.lbHatch = 0 ;

  HPEN hpen =  ::ExtCreatePen(PS_GEOMETRIC | PS_SOLID | (DWORD)cap
                              | (DWORD)join, ROUND_TO_LONG(width),
                              &logBrush, 0, NULL);

  if (hpen != NULL) {
    HPEN oldpen = (HPEN) ::SelectObject((HDC)printDC, hpen);

    if (oldpen != NULL) {
      DeleteObject(oldpen);
    }
  }

  return JNI_TRUE;

  CATCH_BAD_ALLOC_RET (0);
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    setFont
 * Signature: (JLjava/lang/String;FZZIF)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_windows_WPrinterJob_setFont
  (JNIEnv *env, jobject self, jlong printDC, jstring fontName,
   jfloat fontSize, jboolean isBold, jboolean isItalic, jint rotation,
   jfloat awScale)
{
    jboolean didSetFont = JNI_FALSE;

    if (IS_NT) {
        didSetFont = jFontToWFontW(env, (HDC)printDC,
                               fontName,
                               fontSize,
                               isBold,
                               isItalic,
                               rotation,
                               awScale);
    } else {
        didSetFont = jFontToWFontA(env, (HDC)printDC,
                               fontName,
                               fontSize,
                               isBold,
                               isItalic,
                               rotation,
                               awScale);
    }

    return didSetFont;
}

/**
 * Try to convert a java font to a GDI font. On entry, 'printDC',
 * is the device context we want to draw into. 'fontName' is
 * the name of the font to be matched and 'fontSize' is the
 * size of the font in device coordinates. If there is an
 * equivalent GDI font then this function sets that font
 * into 'printDC' and returns a 'true'. If there is no equivalent
 * font then 'false' is returned.
 */
static jboolean jFontToWFontA(JNIEnv *env, HDC printDC, jstring fontName,
                        jfloat fontSize, jboolean isBold, jboolean isItalic,
                        jint rotation, jfloat awScale)
{
    LOGFONTA lf;
    LOGFONTA matchedLogFont;
    BOOL foundFont = false;     // Assume we didn't find a matching GDI font.

    memset(&matchedLogFont, 0, sizeof(matchedLogFont));

    WCHAR* name = TO_WSTRING(fontName);


    /* Some fontnames of Non-ASCII fonts like 'MS Minchou' are themselves
     * Non-ASCII.  They are assumed to be written in Unicode.
     * Hereby, they are converted into platform codeset.
     */
    int maxlen = static_cast<int>(sizeof(lf.lfFaceName)) - 1;
    // maxlen is int due to cbMultiByte parameter is int
    int destLen = WideCharToMultiByte(CP_ACP,   // convert to ASCII code page
                                      0,        // flags
                                      name,     // Unicode string
                                      -1,  // Unicode length is calculated automatically
                                      lf.lfFaceName, // Put ASCII string here
                                      maxlen, // max len
                                      NULL, // default handling of unmappables
                                      NULL);// do not care if def char is used

    /* If WideCharToMultiByte succeeded then the number
     * of bytes it copied into the face name buffer will
     * be creater than zero and we just need to NULL terminate
     * the string. If there was an error then the number of
     * bytes copied is zero and we can not match the font.
     */
    if (destLen > 0) {

        DASSERT(destLen < sizeof(lf.lfFaceName));
        lf.lfFaceName[destLen] = '\0';
        lf.lfCharSet = DEFAULT_CHARSET;
        lf.lfPitchAndFamily = 0;

        foundFont = !EnumFontFamiliesExA((HDC)printDC, &lf,
                                        (FONTENUMPROCA) fontEnumProcA,
                                        (LPARAM) &matchedLogFont, 0);
    }


    if (foundFont) {

        /* Build a font of the requested size with no
         * width modifications. A negative font height
         * tells GDI that we want that values absolute
         * value as the font's point size. If the font
         * is successfully built then set it as the current
         * GDI font.
         */
        matchedLogFont.lfHeight = -ROUND_TO_LONG(fontSize);
        matchedLogFont.lfWidth = 0;
        matchedLogFont.lfEscapement = rotation;
        matchedLogFont.lfOrientation = rotation;
        matchedLogFont.lfUnderline = 0;
        matchedLogFont.lfStrikeOut = 0;

        /* Force bold or italic if requested. The font name
           such as Arial Bold may have already set a weight
           so here we just try to increase it.
        */
        if (isBold) {
            matchedLogFont.lfWeight = embolden(matchedLogFont.lfWeight);
        } else {
            matchedLogFont.lfWeight = FW_REGULAR;
        }

        if (isItalic) {
            matchedLogFont.lfItalic = 0xff;     // TRUE
        }  else {
            matchedLogFont.lfItalic = FALSE;
        }

        HFONT font = CreateFontIndirectA(&matchedLogFont);
        if (font) {
            HFONT oldFont = (HFONT)::SelectObject(printDC, font);
            if (oldFont != NULL) {
                ::DeleteObject(oldFont);
                if (awScale != 1.0) {
                    TEXTMETRIC tm;
                    DWORD avgWidth;
                    GetTextMetrics(printDC, &tm);
                    avgWidth = tm.tmAveCharWidth;
                    matchedLogFont.lfWidth = (LONG)((fabs)(avgWidth*awScale));
                    font = CreateFontIndirectA(&matchedLogFont);
                    if (font) {
                        oldFont = (HFONT)::SelectObject(printDC, font);
                        if (oldFont != NULL) {
                            ::DeleteObject(oldFont);
                            GetTextMetrics(printDC, &tm);
                        } else {
                            foundFont = false;
                        }
                    } else {
                        foundFont = false;
                    }
                }
            } else {
                foundFont = false;
            }
        } else {
            foundFont = false;
        }

    }

    return foundFont ? JNI_TRUE : JNI_FALSE;
}

/**
 * Try to convert a java font to a GDI font. On entry, 'printDC',
 * is the device context we want to draw into. 'fontName' is
 * the name of the font to be matched and 'fontSize' is the
 * size of the font in device coordinates. If there is an
 * equivalent GDI font then this function sets that font
 * into 'printDC' and returns a 'true'. If there is no equivalent
 * font then 'false' is returned.
 */
static jboolean jFontToWFontW(JNIEnv *env, HDC printDC, jstring fontName,
                        jfloat fontSize, jboolean isBold, jboolean isItalic,
                        jint rotation, jfloat awScale)
{
    LOGFONTW lf;
    LOGFONTW matchedLogFont;
    BOOL foundFont = false;     // Assume we didn't find a matching GDI font.

    memset(&matchedLogFont, 0, sizeof(matchedLogFont));

    /* Describe the GDI fonts we want enumerated. We
     * simply supply the java font name and let GDI
     * do the matching. If the java font name is
     * longer than the GDI maximum font lenght then
     * we can't convert the font.
     */
    WCHAR* name = TO_WSTRING(fontName);
    size_t nameLen = wcslen(name);

    if (nameLen < (sizeof(lf.lfFaceName) / sizeof(lf.lfFaceName[0]))) {

        wcscpy(lf.lfFaceName, name);

        lf.lfCharSet = DEFAULT_CHARSET;
        lf.lfPitchAndFamily = 0;

        foundFont = !EnumFontFamiliesExW((HDC)printDC, &lf,
                                        (FONTENUMPROCW) fontEnumProcW,
                                        (LPARAM) &matchedLogFont, 0);
    }

    if (!foundFont) {
        return JNI_FALSE;
    }

    /* Build a font of the requested size with no
     * width modifications. A negative font height
     * tells GDI that we want that values absolute
     * value as the font's point size. If the font
     * is successfully built then set it as the current
     * GDI font.
     */
    matchedLogFont.lfHeight = -ROUND_TO_LONG(fontSize);
    matchedLogFont.lfWidth = 0;
    matchedLogFont.lfEscapement = rotation;
    matchedLogFont.lfOrientation = rotation;
    matchedLogFont.lfUnderline = 0;
    matchedLogFont.lfStrikeOut = 0;

    /* Force bold or italic if requested. The font name
     * such as Arial Bold may have already set a weight
     * so here we just try to increase it.
     */
    if (isBold) {
        matchedLogFont.lfWeight = embolden(matchedLogFont.lfWeight);
    } else {
        matchedLogFont.lfWeight = FW_REGULAR;
    }

    if (isItalic) {
        matchedLogFont.lfItalic = 0xff;     // TRUE
    } else {
        matchedLogFont.lfItalic = FALSE;
    }

    //Debug: dumpLogFont(&matchedLogFont);

    HFONT font = CreateFontIndirectW(&matchedLogFont);
    if (font == NULL) {
        return JNI_FALSE;
    }

    HFONT oldFont = (HFONT)::SelectObject(printDC, font);
    if (oldFont == NULL) { // select failed.
        ::DeleteObject(font);
        return JNI_FALSE;
    }
    ::DeleteObject(oldFont); // no longer needed.

    /* If there is a non-uniform scale then get a new version
     * of the font with an average width that is condensed or
     * expanded to match the average width scaling factor.
     * This is not valid for shearing transforms.
     */
    if (awScale != 1.0) {
        TEXTMETRIC tm;
        DWORD avgWidth;
        GetTextMetrics(printDC, &tm);
        avgWidth = tm.tmAveCharWidth;
        matchedLogFont.lfWidth = (LONG)((fabs)(avgWidth*awScale));
        font = CreateFontIndirectW(&matchedLogFont);
        if (font == NULL) {
            return JNI_FALSE;
        }
        oldFont = (HFONT)::SelectObject(printDC, font);
        if (oldFont == NULL) {
            ::DeleteObject(font);
            return JNI_FALSE;
        } else {
            ::DeleteObject(oldFont);
            return JNI_TRUE;
        }
    }
    return JNI_TRUE;
}

/**
 * Invoked by GDI as a result of the EnumFontFamiliesExW
 * call this routine choses a GDI font that matches
 * a Java font. When a match is found then function
 * returns a zero result to terminate the EnumFontFamiliesExW
 * call. The information about the chosen font is copied into
 * the LOGFONTW structure pointed to by 'lParam'.
 */
static int CALLBACK fontEnumProcW(ENUMLOGFONTEXW *logfont,// logical-font data
                    NEWTEXTMETRICEX *lpntme,              // physical-font data
                    int FontType,                         // type of font
                    LPARAM lParam)
{
    LOGFONTW *matchedLogFont = (LOGFONTW *) lParam;
    int stop = 0;          // Take the first style found.

    if (matchedLogFont != NULL) {
        *matchedLogFont = logfont->elfLogFont;
    }

    return stop;
}

/**
 * Invoked by GDI as a result of the EnumFontFamiliesExA
 * call this routine choses a GDI font that matches
 * a Java font. When a match is found then function
 * returns a zero result to terminate the EnumFontFamiliesExA
 * call. The information about the chosen font is copied into
 * the LOGFONTA structure pointed to by 'lParam'.
 */
static int CALLBACK fontEnumProcA(ENUMLOGFONTEXA *logfont,// logical-font data
                    NEWTEXTMETRICEX *lpntme,              // physical-font data
                    int FontType,                         // type of font
                    LPARAM lParam)
{
    LOGFONTA *matchedLogFont = (LOGFONTA *) lParam;
    int stop = 0;          // Take the first style found.

    if (matchedLogFont != NULL) {
        *matchedLogFont = logfont->elfLogFont;
    }

    return stop;
}

/**
 * Given the weight of a font from a GDI LOGFONT
 * structure, return a new weight indicating a
 * bolder font.
 */
static int embolden(int currentWeight)
{

    /* If the font is less than bold then make
     * it bold. In real life this will mean making
     * a FW_NORMAL font bold.
     */
    if (currentWeight < FW_BOLD) {
        currentWeight = FW_BOLD;

    /* If the font is already bold or bolder
     * then just increase the weight. This will
     * not be visible with GDI in Win95 or NT4.
     */
    } else {
        currentWeight += EMBOLDEN_WEIGHT;
        if (currentWeight > MAX_FONT_WEIGHT) {
            currentWeight = MAX_FONT_WEIGHT;
        }
    }

    return currentWeight;
}

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    setTextColor
 * Signature: (JIII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_setTextColor
(JNIEnv *env, jobject self, jlong printDC, jint red, jint green, jint blue) {

    (void) ::SetTextColor( (HDC)printDC, RGB(red, green, blue));

}

JNIEXPORT jint JNICALL Java_sun_awt_windows_WPrinterJob_getGDIAdvance
    (JNIEnv *env, jobject self, jlong printDC, jstring text)
{
    SIZE size;
    LPWSTR wText = TO_WSTRING(text);
    size_t strLen = wcslen(wText);
    BOOL ok = GetTextExtentPoint32((HDC)printDC, wText, (int)strLen, &size);
    if (ok) {
        return size.cx;
    } else {
        return 0;
    }
}



/*
 * ETO_PDY is conditionally defined in wingdi.h as it is available
 * only on Windows 2000 and later. ie it requires the application
 * define that it is targeting these APIS by placing
 * #define _WIN32_WINNT 0x0500
 * and perhaps
 * #define WINVER 0x5000
 * before including the headers
 * But this causes many problems for AWT headers subsequently included.
 * So instead hard code the value of the flag as our own macro
 * If for any reason this code is executed on Win 9x then this will
 * not be understood and the advances array will be misinterpreted.
 * So we don't use that it in that case and restrict ourselves to x advances.
 * Its possible in some cases that text would then not print as expected.
 * However we will not normally supply y advances so this is a less likely
 * code path and its not worth worrying about in we will not in future
 * support win9x - and definitely not to this extent.
 */
#define J2D_ETO_PDY 0x2000

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    textOut
 * Signature: (JLjava/lang/String;BFF[F)V
 *
 * Generate GDI text calls for the unicode string
 * <code>text</code> into the device context
 * <code>printDC</code>. The text string is
 * positioned at <code>x</code>, <code>y</code>.
 * The positioning of each glyph in the string
 * is determined by windows.
 * If 'glyphCodes' is true then the string is 16 bit glyph indices
 * into the font, not character codes.
 * strLen needs to be passed in for the glyphCodes case since its possible
 * the missing glyph code may be present, and that is always zero, which
 * would be misinterpreted by GDI and the string functions as null termination
 * of the string.
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_textOut
(JNIEnv *env, jobject self, jlong printDC, jstring text, jint strLen,
     boolean glyphCodes, jfloat x, jfloat y, jfloatArray positions)
{

    long posX = ROUND_TO_LONG(x);
    long posY = ROUND_TO_LONG(y);
    int flags = (glyphCodes !=0) ? ETO_GLYPH_INDEX : 0;
    LPWSTR wText = TO_WSTRING(text);

    int *advances = NULL, *xadvances = NULL, *xyadvances = NULL;
    BOOL useYAdvances = FALSE;
    jfloat *glyphPos = NULL;
    if (positions != NULL) {
        glyphPos = env->GetFloatArrayElements(positions, NULL);
    }

    /* We need to convert positions relative to the origin of the text
     * into advances relative to the previous glyph.
     * We expect to be able to allocate these small arrays.
     * If we fail then we'll print the glyphs using their built-in advances.
     * Because the array is of inter-character advances we only need
     * strLen - 1 entries but Windows looks at the advance between
     * the last character and the non-existent character we allocate
     * space for that as well.
     * We supply only the advances that are needed
     * - Default advances (ie none) if GDI advances are what we want
     * - Only X advances if the Y advances are all zero.
     * We allocate two arrays so we can figure out on the fly which
     * we need.
     * Note that we have to add the 'error' or difference between the
     * rounded advance and the floating point advance back into the
     * calculation of the next advance else the sum of the integer-
     * rounded advances will drift away from the true advance.
     */
    if (glyphPos != NULL && strLen > 0) {
         xadvances = (int*)safe_Malloc(strLen * sizeof(int));
         xyadvances = (int*)safe_Malloc(strLen * sizeof(int) * 2);
    }
    if (xadvances != NULL && xyadvances != NULL) {
        int *inxAdvances = xadvances;
        int *inxyAdvances = xyadvances;
        jfloat *inGlyphPos = glyphPos;
        jfloat lastX = *inGlyphPos++;
        jfloat lastY = *inGlyphPos++;
        jfloat errorX = 0, errorY = 0;
        for (int i = 1; i < strLen; i++) {

            jfloat thisX = *inGlyphPos++;
            jfloat thisY = *inGlyphPos++;

            jfloat xAdvance = thisX - lastX + errorX;
            jfloat yAdvance = thisY - lastY + errorY;

            int xadv = ROUND_TO_INT(xAdvance);
            errorX = xAdvance - xadv;
            int yadv = ROUND_TO_INT(yAdvance);
            errorY = yAdvance - yadv;
            if (yadv != 0) {
                useYAdvances = TRUE;
            }
            *inxAdvances++ = xadv;
            *inxyAdvances++ = xadv;
            *inxyAdvances++ = yadv;

            lastX = thisX;
            lastY = thisY;
        }
        /* This is the advance from the last character.
         * It is not technically needed, but the raster
         * drivers, as opposed to the PostScript driver
         * will fail to print the entire string if this
         * value is absurdly large or absurdly negative.
         */
        *inxAdvances = 0;
        *inxyAdvances++ = 0;
        *inxyAdvances = 0;
    }

    if (useYAdvances && IS_WIN2000) {
        advances = xyadvances;
        flags |= J2D_ETO_PDY;
    } else {
        advances = xadvances;
    }

    /* Done with the float array parameter, so release it. */
    if (glyphPos != NULL) {
        env->ReleaseFloatArrayElements(positions, glyphPos, JNI_ABORT);
    }

    BOOL drawn = ::ExtTextOutW( (HDC)printDC,
                    posX, posY,     // starting position for the text
                    flags,          // glyphCodes?, y advances?
                    NULL,           // optional clipping-opaquing rectangle
                    wText,          // the Unicode text to draw
                    static_cast<UINT>(strLen),
                    advances);      // intercharacter advances or NULL

    if (xadvances != NULL) {
        free(xadvances);
    }
    if (xyadvances != NULL) {
        free(xyadvances);
    }
}

/**
 * Scans a 24 bit RGB DIB image looking for the first non-white line.
 * On entry, if scanLineStride is negative, 'image' points at the
 * bottom of the DIB, which is where the first scan line is.
 * Alternatively, if scanLineStride is positive, it's a top-down
 * DIB and 'image'  points to the top scan line.
 * 'numLinesP', on entry, is the number of scan lines in the image while
 * 'width' is the number of 24 bit pixels on each line. If a non-white
 * line is found in the DIB, then a pointer to the first,
 * working from the bottom, non-white scan line is returned.
 * and the number of remaining scan lines is returned in  *'numLinesP'.
 * Pixels are 3 byte BGR triples, so any byte that is not 0xff indicates
 * its a component of a non-white pixel. So we don't need to combine bytes
 * into pixels. Simply scan the image looking for any byte that is not 0xff
 */
static jbyte *findNonWhite(jbyte *image, long sy, long width, long height,
                          long scanLineStride, long *numLinesP) {

    long found = -1;
    long numLines = 0;
    jbyte *startLine = image;
    unsigned char *inLine;
    const unsigned char cc = (unsigned char)0xff;

    assert(image != NULL);
    assert(0 <= sy && sy < height);
    assert(0 < width);
    assert(0 < height);
    assert(numLinesP != NULL);

    for (numLines = 0; sy < height; numLines++, sy++) {

        inLine = (unsigned char*)startLine;

        for (long colcomp = 0; colcomp < abs(scanLineStride); colcomp++) {
            if (*inLine++ != cc) {
                found = sy;
                break;
            }
        }

        if(found != -1) {
            break;
        }

        startLine += scanLineStride;
    }

    *numLinesP = numLines;

    return found == -1 ? NULL : startLine;
}

/* Find the 1st scanline that's entirely white.
 * The starting scanline pointed to by 'image' may be part way through the DIB.
 * If an all white scanline is found, the return value points to the beginning
 * of the last scanline with a non-white pixel. If no all white scanlines
 * are found, the starting scanline is returned.
 * '*numLinesP' returns the number of non-white scan lines.
 * Skip the 1st scanline as its always non-white.
 * If passed scanLineStride is negative, the DIB is bottom-up,
 * otherwise it's top-down.
 */
static jbyte *findWhite(jbyte *image, long sy, long width, long height,
                        long scanLineStride, long *numLinesP) {

    long numLines;
    jbyte *startLine = image;
    unsigned char *inLine;
    jbyte *found = NULL;
    long white;
    const unsigned char cc = (unsigned char)0xff;

    assert(image != NULL);
    assert(0 <= sy);
    assert(0 < width);
    assert(0 < height);
    assert(numLinesP != NULL);

    ++sy;
    for(numLines = 1; sy < height; numLines++, sy++) {

        startLine += scanLineStride;
        inLine = (unsigned char*)startLine;
        white = 1;

        for (long colcomp = 0; colcomp < abs(scanLineStride); colcomp++) {
            if (*inLine++ != cc) {
                white = 0;
                break;
            }
        }

        if (white != 0) {
           found = startLine - scanLineStride;
           break;
        }
    }

    *numLinesP = numLines;

    return found == NULL ? startLine : found;

}

/*
 * Reverses the bitmap.
 * Returns pointer to reversed bitmap (DWORD aligned).
 * Returns NULL if unsuccessful.
 * NOTE: Caller must free the pointer returned by calling free.
 */
static jbyte* reverseDIB(jbyte* imageBits, long srcWidth, long srcHeight,
                          int bitsperpixel) {

    /* get width in bytes.
     * If the image is 24bpp, its srcWidth*3
     * If the image is 8bpp, its just srcWidth
     * If the image is 1bpp or 4bpp one then its rounded up to the next byte.
     */
    long imgWidthByteSz;
    switch (bitsperpixel) {
    case 24 : imgWidthByteSz = srcWidth * 3;
        break;
    case 8 :  imgWidthByteSz = srcWidth;
        break;
    case 1 :  imgWidthByteSz = (srcWidth + 7) / 8 ;
        break;
    case 4 :  imgWidthByteSz = (srcWidth + 1) / 2 ;
        break;
    default : /* not expected but this is OK for any exact multiple of 8 */
        imgWidthByteSz = srcWidth * bitsperpixel / 8;
    }

    int padBytes = 0;
    /* make it DWORD aligned */
    if ((imgWidthByteSz % sizeof(DWORD)) != 0)
        padBytes = sizeof(DWORD) - (imgWidthByteSz % sizeof(DWORD));

    long newImgSize = (imgWidthByteSz+padBytes) * ROUND_TO_LONG(srcHeight);
    jbyte* alignedImage = (jbyte*) safe_Malloc(newImgSize);

    if (alignedImage != NULL) {
        memset(alignedImage, 0xff, newImgSize);

        jbyte* imgLinePtr = alignedImage;
        for (long i=ROUND_TO_LONG(srcHeight)-1; i>=0; i--) {
            memcpy(imgLinePtr, imageBits+(i*imgWidthByteSz),
                   imgWidthByteSz);
            imgLinePtr += (imgWidthByteSz + padBytes);
        }

        return alignedImage;
    }
    return NULL;
}

#if 0

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    drawImageIntRGB
 * Signature: (J[IFFFFFFFFII)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_drawImageIntRGB
  (JNIEnv *env, jobject self,
   jlong printDC, jintArray image,
   jfloat destX, jfloat destY,
   jfloat destWidth, jfloat destHeight,
   jfloat srcX, jfloat srcY,
   jfloat srcWidth, jfloat srcHeight,
   jint srcBitMapWidth, jint srcBitMapHeight) {

    int result = 0;

    assert(printDC != NULL);
    assert(image != NULL);
    assert(srcX >= 0);
    assert(srcY >= 0);
    assert(srcWidth > 0);
    assert(srcHeight > 0);
    assert(srcBitMapWidth > 0);
    assert(srcBitMapHeight > 0);


    static int alphaMask =  0xff000000;
    static int redMask =    0x00ff0000;
    static int greenMask =  0x0000ff00;
    static int blueMask =   0x000000ff;

    struct {
        BITMAPV4HEADER header;
        DWORD          masks[256];
    } dib;



    memset(&dib,0,sizeof(dib));
    dib.header.bV4Size = sizeof(dib.header);
    dib.header.bV4Width = srcBitMapWidth;
    dib.header.bV4Height = -srcBitMapHeight;    // Top down DIB
    dib.header.bV4Planes = 1;
    dib.header.bV4BitCount = 32;
    dib.header.bV4V4Compression = BI_BITFIELDS;
    dib.header.bV4SizeImage = 0;        // It's the default size.
    dib.header.bV4XPelsPerMeter = 0;
    dib.header.bV4YPelsPerMeter = 0;
    dib.header.bV4ClrUsed = 0;
    dib.header.bV4ClrImportant = 0;
    dib.header.bV4RedMask = redMask;
    dib.header.bV4GreenMask = greenMask;
    dib.header.bV4BlueMask = blueMask;
    dib.header.bV4AlphaMask = alphaMask;
    dib.masks[0] = redMask;
    dib.masks[1] = greenMask;
    dib.masks[2] = blueMask;
    dib.masks[3] = alphaMask;

    jint *imageBits = NULL;

    try {
        imageBits = (jint *)env->GetPrimitiveArrayCritical(image, 0);

        if (printDC){
            result = ::StretchDIBits( (HDC)printDC,
                                      ROUND_TO_LONG(destX),
                                      ROUND_TO_LONG(destY),
                                      ROUND_TO_LONG(destWidth),
                                      ROUND_TO_LONG(destHeight),
                                      ROUND_TO_LONG(srcX),
                                      ROUND_TO_LONG(srcY),
                                      ROUND_TO_LONG(srcWidth),
                                      ROUND_TO_LONG(srcHeight),
                                      imageBits,
                                      (BITMAPINFO *)&dib,
                                      DIB_RGB_COLORS,
                                      SRCCOPY);

        }
    } catch (...) {
        if (imageBits != NULL) {
            env->ReleasePrimitiveArrayCritical(image, imageBits, 0);
        }
        throw;
    }

    env->ReleasePrimitiveArrayCritical(image, imageBits, 0);

}
#else

/*
 * Class:     sun_awt_windows_WPrinterJob
 * Method:    drawDIBImage
 * Signature: (J[BFFFFFFFFI[B)V
 */
JNIEXPORT void JNICALL Java_sun_awt_windows_WPrinterJob_drawDIBImage
  (JNIEnv *env, jobject self,
   jlong printDC, jbyteArray image,
   jfloat destX, jfloat destY,
   jfloat destWidth, jfloat destHeight,
   jfloat srcX, jfloat srcY,
   jfloat srcWidth, jfloat srcHeight,
   jint bitCount, jbyteArray bmiColorsArray) {

    int result = 0;

    assert(printDC != NULL);
    assert(image != NULL);
    assert(srcX >= 0);
    assert(srcY >= 0);
    assert(srcWidth > 0);
    assert(srcHeight > 0);

#define MAXCOLS 256
    struct {
        BITMAPINFOHEADER bmiHeader;
        RGBQUAD         bmiColors[MAXCOLS];
    } bmi;

    memset(&bmi, 0, sizeof(bmi));
    bmi.bmiHeader.biSize = sizeof(bmi.bmiHeader);
    bmi.bmiHeader.biWidth = ROUND_TO_LONG(srcWidth);
    bmi.bmiHeader.biHeight = ROUND_TO_LONG(srcHeight);
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = (WORD)bitCount;
    bmi.bmiHeader.biCompression = BI_RGB;
    bmi.bmiHeader.biSizeImage = 0;        // It's the default size.
    bmi.bmiHeader.biXPelsPerMeter = 0;
    bmi.bmiHeader.biYPelsPerMeter = 0;
    bmi.bmiHeader.biClrUsed = 0;
    bmi.bmiHeader.biClrImportant = 0;

    jint *imageBits = NULL;
    try {

        if (bmiColorsArray != NULL) {
            BYTE* bmiCols;
            int numCols = 1<<bitCount;
            if (numCols > MAXCOLS) {
                numCols = MAXCOLS; /* don't write past end of struct */
            }
            bmiCols = (BYTE*)env->GetPrimitiveArrayCritical(bmiColorsArray, 0);
            memcpy(&(bmi.bmiColors[0]), bmiCols, (numCols*4));
            env->ReleasePrimitiveArrayCritical(bmiColorsArray, bmiCols, 0);
        }
        imageBits = (jint *)env->GetPrimitiveArrayCritical(image, 0);

        // Workaround for drivers/apps that do not support top-down.
        // Because we don't know if they support or not,
        // always send bottom-up DIBs.
        jbyte *dibImage = reverseDIB((jbyte*)imageBits,
                                     (long)srcWidth, (long)srcHeight,
                                     bitCount);
        if (dibImage != NULL) {
          if (printDC){
            result = ::StretchDIBits( (HDC)printDC,
                                      ROUND_TO_LONG(destX),
                                      ROUND_TO_LONG(destY),
                                      ROUND_TO_LONG(destWidth),
                                      ROUND_TO_LONG(destHeight),
                                      ROUND_TO_LONG(srcX),
                                      ROUND_TO_LONG(srcY),
                                      ROUND_TO_LONG(srcWidth),
                                      ROUND_TO_LONG(srcHeight),
                                      dibImage,
                                      (BITMAPINFO*)(&bmi),
                                      DIB_RGB_COLORS,
                                      SRCCOPY);
          }

          free(dibImage);
        } /* if (dibImage != NULL) */
    } catch (...) {
        if (imageBits != NULL) {
            env->ReleasePrimitiveArrayCritical(image, imageBits, 0);
        }
        JNU_ThrowInternalError(env, "Problem in WPrinterJob_drawDIBImage");
        return;
    }
    env->ReleasePrimitiveArrayCritical(image, imageBits, 0);

}
#endif

/*
 * An utility function to print passed image byte array to
 * the printDC.
 * browserPrinting flag controls whether the image array
 * used as top-down (browserPrinting == JNI_TRUE) or
 * bottom-up (browserPrinting == JNI_FALSE) DIB.
 */
static void doPrintBand(JNIEnv *env, jboolean browserPrinting,
                        HDC printDC, jbyteArray imageArray,
                        jint x, jint y, jint width, jint height) {

    TRY;

    jbyte *image = NULL;
    try {
        long scanLineStride = J2DRasterBPP * width;
        image = (jbyte *)env->GetPrimitiveArrayCritical(imageArray, 0);
        jbyte *startImage;
        jbyte *endImage = NULL;
        long startY = 0;
        long numLines = 0;

        if (browserPrinting) {
            /* for browser printing use top-down approach */
            startImage =  image;
        } else {
            /* when printing to a real printer dc, the dib
               should bottom-up */
            startImage =  image + (scanLineStride * (height - 1));
            scanLineStride = -scanLineStride;
        }
        do {
            startImage = findNonWhite(startImage, startY, width, height,
                                      scanLineStride, &numLines);

            if (startImage != NULL) {
                startY += numLines;
                endImage = findWhite(startImage, startY, width, height,
                                     scanLineStride, &numLines);
                if (browserPrinting) {
                    /* passing -numLines as height to indicate that
                       we treat the image as a top-down DIB */
                    bitsToDevice(printDC, startImage, x, y + startY, width,
                                 -numLines);
                } else {
                    bitsToDevice(printDC, endImage, x, y + startY, width,
                                 numLines);
                }
                startImage = endImage + scanLineStride;
                startY += numLines;
            }
        } while (startY < height && startImage != NULL);

    } catch (...) {
        if (image != NULL) {
            env->ReleasePrimitiveArrayCritical(imageArray, image, 0);
        }
        throw;
    }

    env->ReleasePrimitiveArrayCritical(imageArray, image, 0);

    CATCH_BAD_ALLOC;

}
static FILE* outfile = NULL;
static int bitsToDevice(HDC printDC, jbyte *image, long destX, long destY,
                        long width, long height) {
    int result = 0;

    assert(printDC != NULL);
    assert(image != NULL);
    assert(destX >= 0);
    assert(destY >= 0);
    assert(width > 0);
    /* height could be negative to indicate that this is a top-down DIB */
//      assert(height > 0);

    struct {
        BITMAPINFOHEADER bmiHeader;
        DWORD*             bmiColors;
    } bitMapHeader;

    memset(&bitMapHeader,0,sizeof(bitMapHeader));
    bitMapHeader.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bitMapHeader.bmiHeader.biWidth = width;
    bitMapHeader.bmiHeader.biHeight = height; // does -height work ever?
    bitMapHeader.bmiHeader.biPlanes = 1;
    bitMapHeader.bmiHeader.biBitCount = 24;
    bitMapHeader.bmiHeader.biCompression = BI_RGB;
    bitMapHeader.bmiHeader.biSizeImage = 0;     // It's the default size.
    bitMapHeader.bmiHeader.biXPelsPerMeter = 0;
    bitMapHeader.bmiHeader.biYPelsPerMeter = 0;
    bitMapHeader.bmiHeader.biClrUsed = 0;
    bitMapHeader.bmiHeader.biClrImportant = 0;
    bitMapHeader.bmiColors = NULL;

    height = abs(height);

    // Workaround for drivers/apps that do not support top-down.
    // Because we don't know if they support or not,
    // always send bottom-up DIBs
    if (bitMapHeader.bmiHeader.biHeight < 0) {
      jbyte *dibImage = reverseDIB(image, width, height, 24);
      if (dibImage != NULL) {
        bitMapHeader.bmiHeader.biWidth = ROUND_TO_LONG(width);
        bitMapHeader.bmiHeader.biHeight = ROUND_TO_LONG(height);

        if (printDC){
          result = ::SetDIBitsToDevice(printDC,
                                ROUND_TO_LONG(destX),   // left of dest rect
                                ROUND_TO_LONG(destY),   // top of dest rect
                                ROUND_TO_LONG(width),   // width of dest rect
                                ROUND_TO_LONG(height),  // height of dest rect
                                0,      // left of source rect
                                0,      // top of source rect
                                0,      // line number of 1st source scan line
                                ROUND_TO_LONG(height),  // number of scan lines
                                dibImage,       // points to the DIB
                                (BITMAPINFO *)&bitMapHeader,
                                DIB_RGB_COLORS);
        }

        free (dibImage);
      }
    } else {
      if (printDC){
          result = ::SetDIBitsToDevice(printDC,
                                destX,  // left of dest rect
                                destY,  // top of dest rect
                                width,  // width of dest rect
                                height, // height of dest rect
                                0,      // left of source rect
                                0,      // top of source rect
                                0,      // line number of 1st source scan line
                                height, // number of source scan lines
                                image,  // points to the DIB
                                (BITMAPINFO *)&bitMapHeader,
                                DIB_RGB_COLORS);
      }
    }

    return result;
}

LRESULT CALLBACK PageDialogWndProc(HWND hWnd, UINT message,
                                   WPARAM wParam, LPARAM lParam)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    switch (message) {
        case WM_COMMAND: {
            if ((LOWORD(wParam) == IDOK) ||
                (LOWORD(wParam) == IDCANCEL))
            {
                // If we recieve on of these two notifications, the dialog
                // is about to be closed. It's time to unblock all the
                // windows blocked by this dialog, as doing so from the
                // WM_DESTROY handler is too late
                jobject peer = (jobject)(::GetProp(hWnd, ModalDialogPeerProp));
                env->CallVoidMethod(peer, AwtPrintDialog::setHWndMID, (jlong)0);
            }
            break;
        }
    }

    return ComCtl32Util::GetInstance().DefWindowProc(NULL, hWnd, message, wParam, lParam);
}

/**
 * Called by the Page Setup dialog this routine makes sure the
 * print dialog becomes the front most window.
 */
static UINT CALLBACK pageDlgHook(HWND hDlg, UINT msg,
                                 WPARAM wParam, LPARAM lParam)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    TRY;

    switch(msg) {
        case WM_INITDIALOG: {
            PAGESETUPDLG *psd = (PAGESETUPDLG *)lParam;
            jobject peer = (jobject)(psd->lCustData);
            env->CallVoidMethod(peer, AwtPrintDialog::setHWndMID,
                                (jlong)hDlg);
            ::SetProp(hDlg, ModalDialogPeerProp, reinterpret_cast<HANDLE>(peer));

            SetForegroundWindow(hDlg);

            // set appropriate icon for parentless dialogs
            jobject awtParent = env->GetObjectField(peer, AwtPrintDialog::parentID);
            if (awtParent == NULL) {
                ::SendMessage(hDlg, WM_SETICON, (WPARAM)ICON_BIG,
                              (LPARAM)AwtToolkit::GetInstance().GetAwtIcon());
            } else {
                env->DeleteLocalRef(awtParent);
            }

            // subclass dialog's parent to receive additional messages
            ComCtl32Util::GetInstance().SubclassHWND(hDlg,
                                                     PageDialogWndProc);

            break;
        }
        case WM_DESTROY: {
            ComCtl32Util::GetInstance().UnsubclassHWND(hDlg,
                                                       PageDialogWndProc,
                                                       NULL);
            ::RemoveProp(hDlg, ModalDialogPeerProp);
            break;
        }
    }

    return (UINT) FALSE;

    CATCH_BAD_ALLOC_RET(TRUE);
}

/**
 *      Create and return a printer device context for the
 *      default printer. If there is no default printer then
 *      return NULL. This fn is used when printing is invoked
 *      and no user dialog was created. So despite its name, it
 *      needs to return a DC which reflects all the applications
 *      settings which the driver might support.
 *      The number of copies is the most important setting.
 */
static HDC getDefaultPrinterDC(JNIEnv *env, jobject printerJob) {
    HDC printDC = NULL;

    int devWillDoCopies = FALSE;
    PRINTDLG pd;
    memset(&pd, 0, sizeof(PRINTDLG));
    pd.lStructSize = sizeof(PRINTDLG);
    pd.Flags = PD_RETURNDEFAULT | PD_RETURNDC;

    if (AwtCommDialog::PrintDlg(&pd)) {
        printDC = pd.hDC;

        /* Find out how many copies the driver can do, and use driver's
         * dmCopies if requested number is within that limit
         */
        int maxCopies = 1;
        int nCopies = getCopies(env, printerJob);
        SAVE_CONTROLWORD
        if (pd.hDevNames != NULL) {
            DEVNAMES *devnames = (DEVNAMES *)::GlobalLock(pd.hDevNames);

            if (devnames != NULL) {
                LPTSTR lpdevnames = (LPTSTR)devnames;
                LPTSTR printer = lpdevnames+devnames->wDeviceOffset;
                LPTSTR port = lpdevnames+devnames->wOutputOffset;
                // if DeviceCapabilities fails, return value is -1
                maxCopies = (int)::DeviceCapabilities(printer, port, DC_COPIES,
                                                      NULL, NULL);
                RESTORE_CONTROLWORD
                if (maxCopies > 1) {
                    devWillDoCopies = TRUE;
                }
            }
            ::GlobalUnlock(pd.hDevNames);
        }

        if ((maxCopies >= nCopies) && (pd.hDevMode != NULL)) {
            DEVMODE *devmode = (DEVMODE *)::GlobalLock(pd.hDevMode);

            if (devmode != NULL) {

                if ((devmode->dmFields & DM_COPIES) && (nCopies > 1)) {
                    devmode->dmCopies = nCopies;
                    HDC tmpDC = ::ResetDC(pd.hDC, devmode);
                    RESTORE_CONTROLWORD
                    if (tmpDC != NULL) {
                        printDC = tmpDC;
                    }
                }
            }
            ::GlobalUnlock(pd.hDevMode);
        }

        /* Not pretty that this is set in a separate place then the DC */
        if (pd.hDevMode != NULL) {
            AwtPrintControl::setPrintHDMode(env, printerJob, pd.hDevMode);
        }
        if (pd.hDevNames != NULL) {
            AwtPrintControl::setPrintHDName(env, printerJob, pd.hDevNames);
        }

        setBooleanField(env, printerJob, DRIVER_COPIES_STR,
                        (devWillDoCopies ? JNI_TRUE : JNI_FALSE));
        setBooleanField(env, printerJob, DRIVER_COLLATE_STR, JNI_FALSE);
        setBooleanField(env, printerJob, USER_COLLATE_STR, JNI_FALSE);

    }

    return printDC;
}


/**
 * Move the description of the page's size and orientation
 * from the PageFormat object 'page' into the structure,
 * 'setup' used by Windows to display the Page Setup dialog.
 */
static void pageFormatToSetup(JNIEnv *env, jobject job,
                              jobject page, PAGESETUPDLG *setup, HDC hDC) {
    RectDouble paperSize;
    RectDouble margins;

    /* Move the orientation from PageFormat to Windows.
     */
    jint orient = getPageFormatOrientation(env, page);
    int gdiOrientation = (orient == PAGEFORMAT_PORTRAIT) ?
        DMORIENT_PORTRAIT : DMORIENT_LANDSCAPE;
    setOrientationInDevMode(setup->hDevMode, orient == PAGEFORMAT_PORTRAIT);

    int units = (setup->Flags & PSD_INTHOUSANDTHSOFINCHES)
                                                ? MM_HIENGLISH
                                                : MM_HIMETRIC;
    jobject paper = getPaper(env, page);
    getPaperValues(env, paper, &paperSize, &margins);
    // Setting the paper size appears to be a futile exercise, as its not one
    // of the values you can initialise - its an out-only arg. Margins are OK.
    // set it into the DEVMODE if there is one ..
    setup->ptPaperSize.x = convertFromPoints(paperSize.width, units);
    setup->ptPaperSize.y = convertFromPoints(paperSize.height, units);

    if (setup->hDevMode != NULL) {

        double paperWidth, paperHeight;
        WORD dmPaperSize = getPrintPaperSize(env, job);
        matchPaperSize(hDC, setup->hDevMode, setup->hDevNames,
                       paperSize.width,  paperSize.height,
                       &paperWidth, &paperHeight, &dmPaperSize);

        DEVMODE *devmode = (DEVMODE *)::GlobalLock(setup->hDevMode);
        if (devmode != NULL) {
          if (dmPaperSize != 0) {
            devmode->dmFields |= DM_PAPERSIZE;
            devmode->dmPaperSize = dmPaperSize;
          }
          else {
            devmode->dmFields |= DM_PAPERLENGTH | DM_PAPERWIDTH
              | DM_PAPERSIZE;
            devmode->dmPaperSize = DMPAPER_USER;
            devmode->dmPaperWidth =
              (short)(convertFromPoints(paperSize.width, MM_LOMETRIC));
            devmode->dmPaperLength =
              (short)(convertFromPoints(paperSize.height, MM_LOMETRIC));
          }
        }
        ::GlobalUnlock(setup->hDevMode);
    }

    // When setting up these values, account for the orientation of the Paper
    // in the PageFormat. In the margins Rect when in portrait mode,
    // width is really right margin, height is really bottom margin.
    if (orient == PAGEFORMAT_PORTRAIT) {
        setup->rtMargin.left = convertFromPoints(margins.x, units);
        setup->rtMargin.top  = convertFromPoints(margins.y, units);
        setup->rtMargin.right = convertFromPoints(margins.width, units);
        setup->rtMargin.bottom = convertFromPoints(margins.height, units);
    } else if (orient == PAGEFORMAT_LANDSCAPE) {
        setup->rtMargin.left = convertFromPoints(margins.height, units);
        setup->rtMargin.top  = convertFromPoints(margins.x, units);
        setup->rtMargin.right = convertFromPoints(margins.y, units);
        setup->rtMargin.bottom = convertFromPoints(margins.width, units);
    } else { // reverse landscape
        setup->rtMargin.left = convertFromPoints(margins.y, units);
        setup->rtMargin.top  = convertFromPoints(margins.width, units);
        setup->rtMargin.right = convertFromPoints(margins.height, units);
        setup->rtMargin.bottom = convertFromPoints(margins.x, units);
    }

    // Set page size here.
}


/**
 * Return an array of POINTS describing the paper sizes supported
 * by the driver identified by 'deviceName' and 'portName'.
 * If there is an error, then NULL is returned.
 */
static POINT *getPaperSizeList(LPCTSTR deviceName, LPCTSTR portName) {
    DWORD numPaperSizes;
    POINT *paperSizes = NULL;

    SAVE_CONTROLWORD
    numPaperSizes = DeviceCapabilities(deviceName, portName,
                                       DC_PAPERSIZE, NULL, NULL);

    if (numPaperSizes > 0) {
        paperSizes = (POINT *)safe_Malloc(sizeof(*paperSizes) * numPaperSizes);

        DWORD result = DeviceCapabilities(deviceName, portName,
                                          DC_PAPERSIZE, (LPTSTR) paperSizes,
                                          NULL);
        if (result == -1) {
            free((char *) paperSizes);
            paperSizes = NULL;
        }
    }
    RESTORE_CONTROLWORD

    return paperSizes;
}

static WORD getOrientationFromDevMode2(HGLOBAL hDevMode) {

    WORD orient = DMORIENT_PORTRAIT;

    if (hDevMode != NULL) {
        LPDEVMODE devMode = (LPDEVMODE) GlobalLock(hDevMode);
        if ((devMode != NULL) && (devMode->dmFields & DM_ORIENTATION)) {
            orient = devMode->dmOrientation;
        }
        GlobalUnlock(hDevMode);
    }
    return orient;
}

/**
 * Get the orientation of the paper described by the printer
 * handle to a device mode structure 'hDevMode'.
 */
static WORD getOrientationFromDevMode(JNIEnv *env, jobject self) {
    return getOrientationFromDevMode2(AwtPrintControl::getPrintHDMode(env, self));
}

/**
 * Set the orientation of the paper described by the printer
 * handle to a device mode structure 'hDevMode'.
 */
static void setOrientationInDevMode(HGLOBAL hDevMode, jboolean isPortrait) {

    if (hDevMode != NULL) {
        LPDEVMODE devMode = (LPDEVMODE) GlobalLock(hDevMode);
        if (devMode != NULL) {
            devMode->dmOrientation = isPortrait
                                    ? DMORIENT_PORTRAIT
                                    : DMORIENT_LANDSCAPE;
            devMode->dmFields |= DM_ORIENTATION;
        }
        GlobalUnlock(hDevMode);
    }
}

/**
 * Return the paper size and margins for the page
 * adjusted to take into account the portrait or
 * landscape orientation of the page. On entry,
 * 'setup' is a filled in structure as returned
 * by PageSetupDlg(). 'paperSize', 'margins',
 * and 'orientation' all point to caller allocated
 * space while will be filled in by this routine
 * with the size, in unknown Windows units, of
 * the paper, of the margins, and an indicator
 * whether the page is in portrait or landscape
 * orientation, respectively.
 */
static void retrievePaperInfo(const PAGESETUPDLG *setup, POINT *paperSize,
                              RECT *margins, jint *orientation, HDC hdc) {
    int orientationKnown = FALSE;

    *paperSize = setup->ptPaperSize;
    int gdiOrientation = DMORIENT_PORTRAIT;

    /* Usually the setup dialog will tell us the
     * orientation of the page, but it may not.
     */
    if (setup->hDevMode != NULL) {
        gdiOrientation = getOrientationFromDevMode2(setup->hDevMode);
        orientationKnown = TRUE;
    }

    /* The driver didn't tell us the paper orientation
     * so we declare it landscape if the paper
     * is wider than it is long. Square paper is
     * declared to be portait.
     */
    if (orientationKnown == FALSE && paperSize->x > paperSize->y) {
        gdiOrientation = DMORIENT_LANDSCAPE;
    }

    *margins = setup->rtMargin;

    // compare margin from page setup dialog with our device printable area
    RectDouble deviceMargin;

    if (getPrintableArea(hdc, setup->hDevMode, &deviceMargin) == TRUE) {
        RECT devMargin;

        int units = (setup->Flags & PSD_INTHOUSANDTHSOFINCHES)
          ? MM_HIENGLISH : MM_HIMETRIC;

        devMargin.left = convertFromPoints(deviceMargin.x*72, units);
        devMargin.top = convertFromPoints(deviceMargin.y*72, units);
        devMargin.bottom = paperSize->y
          - convertFromPoints(deviceMargin.height*72, units)
          - devMargin.top;
        devMargin.right = paperSize->x
          - convertFromPoints(deviceMargin.width*72, units)
          - devMargin.left;

        if (margins->left < devMargin.left) {
            margins->left = devMargin.left;
        }
        if (margins->top < devMargin.top) {
            margins->top = devMargin.top;
        }
        if (margins->bottom < devMargin.bottom) {
            margins->bottom = devMargin.bottom;
        }
        if (margins->right < devMargin.right) {
            margins->right = devMargin.right;
        }
    }

    /* The Paper class expresses the page size in
     * portait mode while Windows returns the paper
     * size adjusted for the orientation. If the
     * orientation is landscape then we want to
     * flip the width and height to get a portait
     * description of the page.
     */
    if (gdiOrientation != DMORIENT_PORTRAIT) {
        long hold = paperSize->x;
        paperSize->x = paperSize->y;
        paperSize->y = hold;

        margins->left = setup->rtMargin.top;
        margins->right = setup->rtMargin.bottom;
        margins->top = setup->rtMargin.right;
        margins->bottom = setup->rtMargin.left;
    }

    if (gdiOrientation == DMORIENT_PORTRAIT) {
        *orientation = PAGEFORMAT_PORTRAIT;
    } else {
        *orientation = PAGEFORMAT_LANDSCAPE;
    }
}

/**
 * Return the number of copies to be printed for a printerJob.
 */
static jint getCopies(JNIEnv *env, jobject printerJob)
{
    // Because this function may call client Java code,
    // we can't run it on the toolkit thread.
    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());


    jclass printerJobClass = env->GetObjectClass(printerJob);
    jmethodID getCopiesID = env->GetMethodID(printerJobClass, GETCOPIES_STR,
                                             GETCOPIES_SIG);
    jint copies = env->CallIntMethod(printerJob, getCopiesID);

    return copies;
}

/**
 * Return a copy of the Paper object attached to the
 * PageFormat object 'page.'
 */
static jobject getPaper(JNIEnv *env, jobject page) {
    // Because this function may call client Java code,
    // we can't run it on the toolkit thread.
    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());


    jclass pageClass = env->GetObjectClass(page);
    jmethodID getPaperID = env->GetMethodID(pageClass, GETPAPER_STR,
                                                        GETPAPER_SIG);

    return env->CallObjectMethod(page, getPaperID);
}

/**
 * Set the Paper object for a PageFormat instance.
 * 'paper' is the new Paper object that must be
 * set into 'page'.
 */
static void setPaper(JNIEnv *env, jobject page, jobject paper) {
    // Because this function may call client Java code,
    // we can't run it on the toolkit thread.
    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());

    jclass pageClass = env->GetObjectClass(page);
    jmethodID setPaperID = env->GetMethodID(pageClass, SETPAPER_STR,
                                                        SETPAPER_SIG);
    env->CallVoidMethod(page, setPaperID, paper);
}

/**
 * Return the integer ID for the orientation in the PageFormat.
 * Caution: this is the Java spec ID, not the GDI ID.
 */
static jint getPageFormatOrientation(JNIEnv *env, jobject page) {
    // Because this function may call client Java code,
    // we can't run it on the toolkit thread.
    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());

    jclass pageClass = env->GetObjectClass(page);
    jmethodID getOrientID = env->GetMethodID(pageClass, GETORIENT_STR,
                                                        GETORIENT_SIG);
    return env->CallIntMethod(page, getOrientID);
}

static void setPageFormatOrientation(JNIEnv *env,
                                     jobject page, jint orientation) {
    // Because this function may call client Java code,
    // we can't run it on the toolkit thread.
    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());

    jclass pageClass = env->GetObjectClass(page);
    jmethodID setOrientID = env->GetMethodID(pageClass, SETORIENT_STR,
                                                        SETORIENT_SIG);
    env->CallVoidMethod(page, setOrientID, orientation);
}

/**
 * Pull the paper size and margins out of the paper object and
 * return them in points.
 */
static void getPaperValues(JNIEnv *env, jobject paper, RectDouble *paperSize,
                          RectDouble *margins, BOOL widthAsMargin) {
    // Because this function may call client Java code,
    // we can't run it on the toolkit thread.
    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());

    jmethodID getID;

    paperSize->x = 0;
    paperSize->y = 0;

    jclass paperClass = env->GetObjectClass(paper);

    getID = env->GetMethodID(paperClass, GETWIDTH_STR, GETWIDTH_SIG);
    paperSize->width = env->CallDoubleMethod(paper, getID);

    getID = env->GetMethodID(paperClass, GETHEIGHT_STR, GETHEIGHT_SIG);
    paperSize->height = env->CallDoubleMethod(paper, getID);

    getID = env->GetMethodID(paperClass, GETIMG_X_STR, GETIMG_X_SIG);
    margins->x = env->CallDoubleMethod(paper, getID);
    if (margins-> x < 0 ) {
        margins-> x = 0;
    }

    getID = env->GetMethodID(paperClass, GETIMG_Y_STR, GETIMG_Y_SIG);
    margins->y = env->CallDoubleMethod(paper, getID);
    if (margins-> y < 0 ) {
        margins-> y = 0;
    }

    getID = env->GetMethodID(paperClass, GETIMG_W_STR, GETIMG_W_SIG);
    if (widthAsMargin) {
        margins->width = paperSize->width - margins->x
                                      - env->CallDoubleMethod(paper, getID);
    } else {
        margins->width = env->CallDoubleMethod(paper, getID);
    }

    if (margins->width < 0) {
        margins->width = 0;
    }

    getID = env->GetMethodID(paperClass, GETIMG_H_STR, GETIMG_H_SIG);
    if (widthAsMargin) {
        margins->height = paperSize->height - margins->y
                                        - env->CallDoubleMethod(paper, getID);
    } else {
        margins->height = env->CallDoubleMethod(paper, getID);
    }

    if (margins->height < 0) {
        margins->height = 0;
    }

}

/**
 * Given a RECT specifying the margins
 * for the page and an indication of whether
 * the units are 1000ths of an inch (MM_HIENGLISH)
 * or 100ths of a millimeter (MM_HIMETRIC),
 * convert the margins to 72nds of an inch
 * and set them into the PageFormat insance provided.
 */
static void setPaperValues(JNIEnv *env, jobject paper, const POINT *paperSize,
                                         const RECT *margins, int units) {
    // Because this function may call client Java code,
    // we can't run it on the toolkit thread.
    DASSERT(AwtToolkit::MainThread() != ::GetCurrentThreadId());

    jclass paperClass = env->GetObjectClass(paper);
    jmethodID setSizeID = env->GetMethodID(paperClass,
                                        SETSIZE_STR, SETSIZE_SIG);
    jmethodID setImageableID = env->GetMethodID(paperClass,
                                        SETIMAGEABLE_STR, SETIMAGEABLE_SIG);

    /* Set the physical size of the paper.
     */
    jdouble paperWidth = convertToPoints(paperSize->x, units);
    jdouble paperHeight = convertToPoints(paperSize->y, units);
    env->CallVoidMethod(paper, setSizeID, paperWidth, paperHeight);

    /* Set the margins of the paper. In Windows' margin RECT,
     * the right and bottom parts of the structure are not
     * really the right and bottom of the imageable rectangle,
     * but rather the right and bottom margins.
     */
    jdouble x = convertToPoints(margins->left, units);
    jdouble y = convertToPoints(margins->top, units);
    long intWidth = paperSize->x - margins->left - margins->right;
    long intHeight = paperSize->y - margins->top - margins->bottom;
    jdouble width = convertToPoints(intWidth, units);
    jdouble height = convertToPoints(intHeight, units);
    env->CallVoidMethod(paper, setImageableID, x, y, width, height);

}

/**
 * Convert 'value' a measurement in 1/72's of an inch to
 * the units specified by 'units' - either MM_HIENGLISH
 * MM_HIMETRIC, or MM_LOMETRIC. The converted value is returned as
 * a long.
 */
static long convertFromPoints(double value, int units) {
    double conversion = 0;

    switch (units){
     case MM_HIENGLISH:
        conversion = POINTS_TO_HIENGLISH;
        break;

     case MM_HIMETRIC:
        conversion = POINTS_TO_HIMETRIC;
        break;

     case MM_LOMETRIC:
        conversion = POINTS_TO_LOMETRIC;
        break;

     default:
        assert(FALSE);  // Unsupported unit.
    }

    // Adding 0.5 ensures that the integer portion has the expected magnitude
    // before truncation occurs as result of converting from double to long.
    return (long) ((value * conversion) + 0.5);
}

/**
 * Convert a measurement, 'value', from the units
 * specified by 'units', either MM_HIENGLISH or
 * MM_HIMETRIC to 1/72's of an inch and returned
 * as a double.
 */
static double convertToPoints(long value, int units) {
    double convertedValue = (double)value;

    switch (units){
    case MM_HIENGLISH:
        //convertedValue *= HIENGLISH_TO_POINTS;
        // this order of calculation is for bug 4191615
        convertedValue = (convertedValue*72.0) / 1000.0;
        break;

    case MM_HIMETRIC:
        convertedValue *= HIMETRIC_TO_POINTS;
        break;

    case MM_LOMETRIC:
        convertedValue *= LOMETRIC_TO_POINTS;
        break;

    default:
        assert(FALSE);  // Unsupported unit.
    }

    //Need to round off to the precision of the initial value. FIX.

    return convertedValue;
}

/**
 *      Ask the printer device context, 'printDC' about
 *      its capabilities and set these into the WPrintJob2D
 *      object 'self'.
 */
void setCapabilities(JNIEnv *env, jobject self, HDC printDC) {

    // width of page in pixels
    jint pageWid = GetDeviceCaps(printDC, PHYSICALWIDTH);
    setIntField(env, self, PAGEW_STR, pageWid);

    // height of page in pixels
    jint pageHgt = GetDeviceCaps(printDC, PHYSICALHEIGHT);
    setIntField(env, self, PAGEH_STR, pageHgt);

    // x scaling factor of printer
    jint xsf = GetDeviceCaps(printDC, SCALINGFACTORX);

    // x scaling factor of printer
    jint ysf = GetDeviceCaps(printDC, SCALINGFACTORY);

    if (getOrientationFromDevMode(env, self) == DMORIENT_LANDSCAPE) {
        // because we do our own rotation, we should force
        // orientation to portrait so we will get correct page dimensions.

        HGLOBAL hDevMode = AwtPrintControl::getPrintHDMode(env, self);
        if (hDevMode != NULL) {
            DEVMODE *devmode = (DEVMODE*)::GlobalLock(hDevMode);
            if (devmode != NULL) {
                devmode->dmFields |= DM_ORIENTATION;
                devmode->dmOrientation = DMORIENT_PORTRAIT;
                SAVE_CONTROLWORD
                ::ResetDC(printDC, devmode);
                RESTORE_CONTROLWORD
            }
            GlobalUnlock(hDevMode);
        }
    }

    // pixels per inch in x direction
    jint xRes = GetDeviceCaps(printDC, LOGPIXELSX);
    setIntField(env, self, XRES_STR, xRes);

    // pixels per inch in y direction
    jint yRes = GetDeviceCaps(printDC, LOGPIXELSY);
    setIntField(env, self, YRES_STR, yRes);

    // x coord of printable area in pixels
    jint xOrg = GetDeviceCaps(printDC, PHYSICALOFFSETX);
    setIntField(env, self, PHYSX_STR, xOrg);

    // y coord of printable area in pixels
    jint yOrg = GetDeviceCaps(printDC, PHYSICALOFFSETY);
    setIntField(env, self, PHYSY_STR, yOrg);

    // width of printable area in pixels
    jint printWid = GetDeviceCaps(printDC, HORZRES);
    setIntField(env, self, PHYSW_STR, printWid);

    // height of printable area in pixels
    jint printHgt = GetDeviceCaps(printDC, VERTRES);
    setIntField(env, self, PHYSH_STR, printHgt);

}


static inline WORD getPrintPaperSize(JNIEnv *env, jobject self) {
    return (WORD)getIntField(env, self, PRINTPAPERSIZE_STR);
}

static inline void setPrintPaperSize(JNIEnv *env, jobject self, WORD sz) {
    setIntField(env, self, PRINTPAPERSIZE_STR, (jint)sz);
}

/**
 *      Return the java int value of the field 'fieldName' in the
 *      java instance 'self'.
 */
static jint getIntField(JNIEnv *env, jobject self, const char *fieldName) {
    jfieldID fieldId = getIdOfIntField(env, self, fieldName);
    return env->GetIntField(self, fieldId);
}

/**
 *      Return the java long value of the field 'fieldName' in the
 *      java instance 'self'.
 */
static jlong getLongField(JNIEnv *env, jobject self, const char *fieldName) {
    jfieldID fieldId = getIdOfLongField(env, self, fieldName);
    return env->GetLongField(self, fieldId);
}

/**
 *      Set the int field named 'fieldName' of the java instance
 *      'self' to the value 'value'.
 */
static void setIntField(JNIEnv *env, jobject self, const char *fieldName,
                                                                jint value) {
    jfieldID fieldId = getIdOfIntField(env, self, fieldName);
    env->SetIntField(self, fieldId, value);
}

/**
 *      Set the long field named 'fieldName' of the java instance
 *      'self' to the value 'value'.
 */
static void setLongField(JNIEnv *env, jobject self, const char *fieldName,
                                                                jlong value) {
    jfieldID fieldId = getIdOfLongField(env, self, fieldName);
    env->SetLongField(self, fieldId, value);
}

/**
 *      Return the field id of the java instance 'self' of the
 *      java int field named 'fieldName'.
 */
static jfieldID getIdOfIntField(JNIEnv *env, jobject self,
                                                const char *fieldName) {
    jclass myClass = env->GetObjectClass(self);
    jfieldID fieldId = env->GetFieldID(myClass, fieldName, kJavaIntStr);
    DASSERT(fieldId != 0);

    return fieldId;

}

/**
 *      Return the field id of the java instance 'self' of the
 *      java long field named 'fieldName'.
 */
static jfieldID getIdOfLongField(JNIEnv *env, jobject self,
                                                const char *fieldName) {
    jclass myClass = env->GetObjectClass(self);
    jfieldID fieldId = env->GetFieldID(myClass, fieldName, kJavaLongStr);
    DASSERT(fieldId != 0);

    return fieldId;

}

static void setBooleanField(JNIEnv *env, jobject self, const char *fieldName,
                                                              jboolean value) {
    jclass myClass = env->GetObjectClass(self);
    jfieldID fieldId = env->GetFieldID(myClass, fieldName, "Z");
    DASSERT(fieldId != 0);
    env->SetBooleanField(self, fieldId, value);
}

/**
 *  Throw a PrinterException with a string describing
 *  the Window's system error 'err'.
 */
static void throwPrinterException(JNIEnv *env, DWORD err) {
    char errStr[256];
    TCHAR t_errStr[256];
    jclass printerException = env->FindClass(PRINTEREXCEPTION_STR);

    errStr[0] = '\0';
    FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                  NULL,
                  err,
                  MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                  t_errStr,
                  sizeof(t_errStr),
                  NULL );

    WideCharToMultiByte(CP_UTF8, 0, t_errStr, -1,
                        errStr, sizeof(errStr), NULL, NULL);
    env->ThrowNew(printerException, errStr);
}


/*
 * Finds the closest matching paper size for the printer.
 * Parameters are in 72ndths of an inch.
 * paperSize is the win32 integer identifier for a paper size.
 * Requires an initialised set of printer device structures.
 * Updates the printDC to specify the matched paper size.
 * If the passed in paper size is non-zero, its taken to be a windows
 * paper size "name", and we check that paper size against the paper
 * we are matching and prefer that name over other names which also match
 * the size.
 */
static void matchPaperSize(HDC printDC, HGLOBAL hDevMode, HGLOBAL hDevNames,
                           double origWid, double origHgt,
                           double* newWid, double *newHgt,
                           WORD* paperSize) {

    const double epsilon = 0.50;
    const double tolerance = (1.0 * 72.0);  // # inches * 72

    *newWid = origWid;
    *newHgt = origHgt;

   /* 1st check if the DC/Devmode has as its current papersize a paper
    * which matches the paper specified. If yes, then we can skip hunting
    * for the match and in the process we avoid finding a "name" for
    * the paper size which isn't the one the user specified in the page
    * setup dialog. For example "11x17" is also "Ledger".
    */
    if (printDC != NULL) {
      // pixels per inch in x and y direction
      jint xPixelRes = GetDeviceCaps(printDC, LOGPIXELSX);
      jint yPixelRes = GetDeviceCaps(printDC, LOGPIXELSY);

      // width and height of page in pixels
      jint pagePixelWid = GetDeviceCaps(printDC, PHYSICALWIDTH);
      jint pagePixelHgt = GetDeviceCaps(printDC, PHYSICALHEIGHT);

      // page size in 1/72"
      jdouble paperWidth = (jdouble)((pagePixelWid * 72)/(jdouble)xPixelRes);
      jdouble paperHeight = (jdouble)((pagePixelHgt * 72)/(jdouble)yPixelRes);

      if ((fabs(origWid - paperWidth) < epsilon) &&
          (fabs(origHgt - paperHeight) < epsilon) &&
          (*paperSize == 0)) {

        *newWid = origWid;
        *newHgt = origHgt;

        if (hDevMode != NULL) {
          DEVMODE *devmode = (DEVMODE *)::GlobalLock(hDevMode);
          if (devmode != NULL && (devmode->dmFields & DM_PAPERSIZE)) {
            *paperSize = devmode->dmPaperSize;
          }
          ::GlobalUnlock(hDevMode);
        }
        return;
      }
    }

    /* begin trying to match papers */

    LPTSTR printer = NULL, port = NULL;
    if (hDevNames != NULL) {
        DEVNAMES *devnames = (DEVNAMES *)::GlobalLock(hDevNames);
        if (devnames != NULL) {
            LPTSTR lpdevnames = (LPTSTR)devnames;
            printer = _tcsdup(lpdevnames+devnames->wDeviceOffset);
            port = _tcsdup(lpdevnames+devnames->wOutputOffset);
        }
        ::GlobalUnlock(hDevNames);
    }

    //REMIND: code duplicated in AwtPrintControl::getNearestMatchingPaper
    int numPaperSizes = 0;
    WORD *papers = NULL;
    POINT *paperSizes = NULL;

    SAVE_CONTROLWORD
    numPaperSizes = (int)DeviceCapabilities(printer, port, DC_PAPERSIZE,
                                            NULL, NULL);
    if (numPaperSizes > 0) {
        papers = (WORD*)safe_Malloc(sizeof(WORD) * numPaperSizes);
        paperSizes = (POINT *)safe_Malloc(sizeof(*paperSizes) * numPaperSizes);

        DWORD result1 = DeviceCapabilities(printer, port,
                                           DC_PAPERS, (LPTSTR) papers, NULL);
        DWORD result2 = DeviceCapabilities(printer, port,
                                           DC_PAPERSIZE, (LPTSTR) paperSizes,
                                           NULL);

        if (result1 == -1 || result2 == -1 ) {
            free((char *) papers);
            papers = NULL;
            free((char *) paperSizes);
            paperSizes = NULL;
        }
    }

    RESTORE_CONTROLWORD
    double closestWid = 0.0;
    double closestHgt = 0.0;
    WORD   closestMatch = 0;

    if (paperSizes != NULL) {

        /* Paper sizes are in 0.1mm units. Convert to 1/72"
         * For each paper size, compute the difference from the paper size
         * passed in. Use a least-squares difference, so paper much different
         * in x or y should score poorly
         */
        double diffw = origWid;
        double diffh = origHgt;
        double least_square = diffw * diffw + diffh * diffh;
        double tmp_ls;
        double widpts, hgtpts;

        for (int i=0;i<numPaperSizes;i++) {
            widpts = paperSizes[i].x * LOMETRIC_TO_POINTS;
            hgtpts = paperSizes[i].y * LOMETRIC_TO_POINTS;

            if ((fabs(origWid - widpts) < epsilon) &&
                (fabs(origHgt - hgtpts) < epsilon)) {

              if ((*paperSize == 0) || ((*paperSize !=0) &&
                                        (papers[i]==*paperSize))) {
                closestWid = origWid;
                closestHgt = origHgt;
                closestMatch = papers[i];
                break;
              }
            }

            diffw = fabs(widpts - origWid);
            diffh = fabs(hgtpts - origHgt);
            tmp_ls = diffw * diffw + diffh * diffh;
            if ((diffw < tolerance) && (diffh < tolerance) &&
                (tmp_ls < least_square)) {
              least_square = tmp_ls;
              closestWid = widpts;
              closestHgt = hgtpts;
              closestMatch = papers[i];
            }
        }
    }

    if (closestWid > 0) {
        *newWid = closestWid;
    }
    if (closestHgt > 0) {
        *newHgt = closestHgt;
    }

    *paperSize = closestMatch;

    /* At this point we have the paper which is the closest match
     * We now need to select the paper into the DEVMODE, and
     * get a DC which matches so we can get the margins.
     */

    if ((printDC != NULL) && (hDevMode != NULL) && (closestMatch != 0)) {
        DEVMODE *devmode = (DEVMODE *)::GlobalLock(hDevMode);
        if ((devmode != NULL) && (closestMatch != devmode->dmPaperSize)) {
            devmode->dmFields |= DM_PAPERSIZE;
            devmode->dmPaperSize = closestMatch;
            ::ResetDC(printDC, devmode);
            RESTORE_CONTROLWORD
        }
        ::GlobalUnlock(hDevMode);
    }

    if (printer != NULL) {
        free((char *)printer);
    }
    if (port != NULL) {
        free((char *)port);
    }
    if (papers != NULL) {
        free((char *)papers);
    }
    if (paperSizes != NULL) {
        free((char *)paperSizes);
    }

}


static BOOL SetPrinterDevice(LPTSTR pszDeviceName, HGLOBAL* p_hDevMode,
                             HGLOBAL* p_hDevNames)
{
  // Open printer and obtain PRINTER_INFO_2 structure.
  HANDLE hPrinter;
  if (::OpenPrinter(pszDeviceName, &hPrinter, NULL) == FALSE)
    return FALSE;

  DWORD dwBytesReturned, dwBytesNeeded;
  ::GetPrinter(hPrinter, 2, NULL, 0, &dwBytesNeeded);
  PRINTER_INFO_2* p2 = (PRINTER_INFO_2*)::GlobalAlloc(GPTR,
                                                    dwBytesNeeded);
  if (p2 == NULL) {
    ::ClosePrinter(hPrinter);
    return FALSE;
  }

  if (::GetPrinter(hPrinter, 2, (LPBYTE)p2, dwBytesNeeded,
                   &dwBytesReturned) == 0) {
    ::GlobalFree(p2);
    ::ClosePrinter(hPrinter);
    return FALSE;
  }

  DEVMODE *pDevMode = NULL;
  HGLOBAL  hDevMode = NULL;
  /* If GetPrinter didn't fill in the DEVMODE, try to get it by calling
     DocumentProperties...
     */
  if (p2->pDevMode == NULL){
    SAVE_CONTROLWORD
    LONG bytesNeeded = ::DocumentProperties(NULL, hPrinter,
                                          pszDeviceName,
                                          NULL, NULL, 0);
    RESTORE_CONTROLWORD

   if (bytesNeeded <= 0) {
      ::GlobalFree(p2);
      ::ClosePrinter(hPrinter);
      return FALSE;
    }

    hDevMode = ::GlobalAlloc(GHND, bytesNeeded);
    if (hDevMode == NULL) {
      ::GlobalFree(p2);
      ::ClosePrinter(hPrinter);
      return FALSE;
    }

    pDevMode = (DEVMODE*)::GlobalLock(hDevMode);
    if (pDevMode == NULL) {
      ::GlobalFree(hDevMode);
      ::GlobalFree(p2);
      ::ClosePrinter(hPrinter);
      return FALSE;
    }

    LONG lFlag = ::DocumentProperties(NULL, hPrinter,
                                    pszDeviceName,
                                    pDevMode, NULL,
                                    DM_OUT_BUFFER);
    RESTORE_CONTROLWORD
    if (lFlag != IDOK) {
      ::GlobalUnlock(hDevMode);
      ::GlobalFree(hDevMode);
      ::GlobalFree(p2);
      ::ClosePrinter(hPrinter);
      return FALSE;
    }

  } else {
    // Allocate a global handle for DEVMODE and copy DEVMODE data.
    hDevMode = ::GlobalAlloc(GHND,
                             (sizeof(*p2->pDevMode) + p2->pDevMode->dmDriverExtra));
    if (hDevMode == NULL) {
      ::GlobalFree(p2);
      ::ClosePrinter(hPrinter);
      return FALSE;
    }

    pDevMode = (DEVMODE*)::GlobalLock(hDevMode);
    if (pDevMode == NULL) {
      ::GlobalFree(hDevMode);
      ::GlobalFree(p2);
      ::ClosePrinter(hPrinter);
      return FALSE;
    }

    memcpy(pDevMode, p2->pDevMode,
           sizeof(*p2->pDevMode) + p2->pDevMode->dmDriverExtra);
  }

  ::GlobalUnlock(hDevMode);
  ::ClosePrinter(hPrinter);

  // Compute size of DEVNAMES structure you'll need.
  // All sizes are WORD as in DEVNAMES structure
  // All offsets are in characters, not in bytes
  WORD drvNameLen = static_cast<WORD>(_tcslen(p2->pDriverName));  // driver name
  WORD ptrNameLen = static_cast<WORD>(_tcslen(p2->pPrinterName)); // printer name
  WORD porNameLen = static_cast<WORD>(_tcslen(p2->pPortName));    // port name
  WORD devNameSize = static_cast<WORD>(sizeof(DEVNAMES)) +
    (ptrNameLen + porNameLen + drvNameLen + 3)*sizeof(TCHAR);

  // Allocate a global handle big enough to hold DEVNAMES.
  HGLOBAL   hDevNames = ::GlobalAlloc(GHND, devNameSize);
  DEVNAMES* pDevNames = (DEVNAMES*)::GlobalLock(hDevNames);

  // Copy the DEVNAMES information from PRINTER_INFO_2 structure.
  pDevNames->wDriverOffset = sizeof(DEVNAMES)/sizeof(TCHAR);
  memcpy((LPTSTR)pDevNames + pDevNames->wDriverOffset,
         p2->pDriverName, drvNameLen*sizeof(TCHAR));

   pDevNames->wDeviceOffset = static_cast<WORD>(sizeof(DEVNAMES)/sizeof(TCHAR)) +
   drvNameLen + 1;
   memcpy((LPTSTR)pDevNames + pDevNames->wDeviceOffset,
       p2->pPrinterName, ptrNameLen*sizeof(TCHAR));

   pDevNames->wOutputOffset = static_cast<WORD>(sizeof(DEVNAMES)/sizeof(TCHAR)) +
     drvNameLen + ptrNameLen + 2;
   memcpy((LPTSTR)pDevNames + pDevNames->wOutputOffset,
          p2->pPortName, porNameLen*sizeof(TCHAR));

   pDevNames->wDefault = 0;

   ::GlobalUnlock(hDevNames);
   ::GlobalFree(p2);   // free PRINTER_INFO_2

   *p_hDevMode = hDevMode;
   *p_hDevNames = hDevNames;

   return TRUE;
}


JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_setNativePrintService(JNIEnv *env,
                                                       jobject name,
                                                       jstring printer)
{
    TRY;
    LPTSTR printerName = (LPTSTR)JNU_GetStringPlatformChars(env,
                                                            printer, NULL);
    HDC hDC = AwtPrintControl::getPrintDC(env, name);
    if (hDC != NULL) {
        DeletePrintDC(hDC);
      hDC = NULL;
    }

    SAVE_CONTROLWORD
    hDC = ::CreateDC(TEXT("WINSPOOL"), printerName, NULL, NULL);
    RESTORE_CONTROLWORD
    if (hDC == NULL) {
        jclass printerException = env->FindClass(PRINTEREXCEPTION_STR);
        env->ThrowNew(printerException, "Invalid name of PrintService.");
        JNU_ReleaseStringPlatformChars(env, printer, printerName);
        return;
    }
    AwtPrintControl::setPrintDC(env, name, hDC);

    HANDLE hDevMode = AwtPrintControl::getPrintHDMode(env, name);
    if (hDevMode != NULL) {
      ::GlobalFree(hDevMode);
      hDevMode = NULL;
    }

    HANDLE hDevNames = AwtPrintControl::getPrintHDName(env, name);;
    if (hDevNames != NULL) {
      ::GlobalFree(hDevNames);
      hDevNames = NULL;
    }

    SetPrinterDevice(printerName, &hDevMode, &hDevNames);

    AwtPrintControl::setPrintHDMode(env, name, hDevMode);
    AwtPrintControl::setPrintHDName(env, name, hDevNames);

    // Driver capability for copies & collation are not set
    // when printDialog and getDefaultPrinterDC are not called.
    // set DRIVER_COPIES_STR and DRIVER_COLLATE_STR
    DEVMODE *devmode = NULL;
    if (hDevMode != NULL) {
        devmode = (DEVMODE *)::GlobalLock(hDevMode);
        DASSERT(!IsBadReadPtr(devmode, sizeof(DEVMODE)));
    }

    if (devmode != NULL) {
        if (devmode->dmFields & DM_COPIES) {
          setBooleanField(env, name, DRIVER_COPIES_STR, JNI_TRUE);
        }

        if (devmode->dmFields & DM_COLLATE) {
           setBooleanField(env, name, DRIVER_COLLATE_STR, JNI_TRUE);
        }

        ::GlobalUnlock(hDevMode);
    }

    setCapabilities(env, name, hDC);

    JNU_ReleaseStringPlatformChars(env, printer, printerName);
    CATCH_BAD_ALLOC;

}


JNIEXPORT jstring JNICALL
Java_sun_awt_windows_WPrinterJob_getNativePrintService(JNIEnv *env,
                                                       jobject name)
{
    TRY;
    jstring printer;
    HANDLE hDevNames = AwtPrintControl::getPrintHDName(env, name);
    if (hDevNames == NULL) {
        return NULL;
    }
    DEVNAMES* pDevNames = (DEVNAMES*)::GlobalLock(hDevNames);

    printer = JNU_NewStringPlatform(env,
                                    (LPTSTR)pDevNames+pDevNames->wDeviceOffset);
    ::GlobalUnlock(hDevNames);
    return printer;

    CATCH_BAD_ALLOC_RET(0);
}

static BOOL getPrintableArea(HDC pdc, HANDLE hDevMode, RectDouble *margin)
{
    if (pdc == NULL) {
      return FALSE;
    }

    DEVMODE *pDevMode = (DEVMODE*)::GlobalLock(hDevMode);
    if (pDevMode == NULL) {
        return FALSE;
    }

    SAVE_CONTROLWORD
    ::ResetDC(pdc, pDevMode);
    RESTORE_CONTROLWORD

    int left = GetDeviceCaps(pdc, PHYSICALOFFSETX);
    int top = GetDeviceCaps(pdc, PHYSICALOFFSETY);
    int width = GetDeviceCaps(pdc, HORZRES);
    int height = GetDeviceCaps(pdc, VERTRES);
    int resx = GetDeviceCaps(pdc, LOGPIXELSX);
    int resy = GetDeviceCaps(pdc, LOGPIXELSY);


    margin->x = (jdouble)left/resx;
    margin->y =(jdouble)top/resy;
    margin->width = (jdouble)width/resx;
    margin->height = (jdouble)height/resy;

    ::GlobalUnlock(hDevMode);

    return TRUE;
}

JNIEXPORT void JNICALL
Java_sun_awt_windows_WPrinterJob_initIDs(JNIEnv *env, jclass cls)
{
    TRY;

    AwtPrintDialog::controlID =
      env->GetFieldID(cls, "pjob", "Ljava/awt/print/PrinterJob;");
    jclass printDialogPeerClass = env->FindClass("Lsun/awt/windows/WPrintDialogPeer;");
    AwtPrintDialog::setHWndMID =
      env->GetMethodID(printDialogPeerClass, "setHWnd", "(J)V");

    DASSERT(AwtPrintDialog::controlID != NULL);
    DASSERT(AwtPrintDialog::setHWndMID != NULL);

    AwtPrintControl::initIDs(env, cls);
    CATCH_BAD_ALLOC;
}

} /* extern "C" */
