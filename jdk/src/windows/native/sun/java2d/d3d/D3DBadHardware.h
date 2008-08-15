/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef D3DBADHARDWARE_H
#define D3DBADHARDWARE_H

#include "D3DPipeline.h"
#include "D3DPipelineManager.h"

typedef struct ADAPTER_INFO {
  DWORD    VendorId;
  DWORD    DeviceId;
  LONGLONG DriverVersion; // minimum driver version to pass, or NO_VERSION
  USHORT   OsInfo;        // OSes where the DriverVersion is relevant or, OS_ALL
} ADAPTER_INFO;

// this DeviceId means that all vendor boards are to be excluded
#define ALL_DEVICEIDS (0xffffffff)

#define D_VERSION(H1, H2, L1, L2) \
  (((LONGLONG)((H1 << 16) | H2) << 32) | ((L1 << 16) | (L2)))

// this driver version is used to pass the driver version check
// as it is always greater than any driver version
#define MAX_VERSION D_VERSION(0x7fff, 0x7fff, 0x7fff, 0x7fff)
// this DriverVersion means that the version of the driver doesn't matter,
// all versions must fail ("there's no version of the driver that passes")
#define NO_VERSION D_VERSION(0xffff, 0xffff, 0xffff, 0xffff)

static const ADAPTER_INFO badHardware[] = {

    // any Intel chip
    // Reason: workaround for 6620073, 6612195, 6620073
    { 0x8086, ALL_DEVICEIDS, NO_VERSION, OS_ALL },

    // ATI Mobility Radeon X1600, X1400, X1450, X1300, X1350
    // Reason: workaround for 6613066, 6687166
    // X1300 (four sub ids)
    { 0x1002, 0x714A, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x714A, D_VERSION(7,14,10,0567), OS_VISTA },
    { 0x1002, 0x7149, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x7149, D_VERSION(7,14,10,0567), OS_VISTA },
    { 0x1002, 0x714B, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x714B, D_VERSION(7,14,10,0567), OS_VISTA },
    { 0x1002, 0x714C, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x714C, D_VERSION(7,14,10,0567), OS_VISTA },
    // X1350 (three sub ids)
    { 0x1002, 0x718B, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x718B, D_VERSION(7,14,10,0567), OS_VISTA },
    { 0x1002, 0x718C, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x718C, D_VERSION(7,14,10,0567), OS_VISTA },
    { 0x1002, 0x7196, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x7196, D_VERSION(7,14,10,0567), OS_VISTA },
    // X1400
    { 0x1002, 0x7145, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x7145, D_VERSION(7,14,10,0567), OS_VISTA },
    // X1450 (two sub ids)
    { 0x1002, 0x7186, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x7186, D_VERSION(7,14,10,0567), OS_VISTA },
    { 0x1002, 0x718D, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x718D, D_VERSION(7,14,10,0567), OS_VISTA },
    // X1600
    { 0x1002, 0x71C5, D_VERSION(6,14,10,6706), OS_WINXP },
    { 0x1002, 0x71C5, D_VERSION(7,14,10,0567), OS_VISTA },

    // Nvidia Quadro NVS 110M
    // Reason: workaround for 6629891
    { 0x10DE, 0x01D7, D_VERSION(6,14,11,5665), OS_WINXP },

    // Nvidia Quadro PCI-E series
    // Reason: workaround for 6653860
    { 0x10DE, 0x00FD, D_VERSION(6,14,10,6573), OS_WINXP },

    // Nvidia GeForce 6200 TurboCache(TM)
    // Reason: workaround for 6588384
    { 0x10DE, 0x0161, NO_VERSION, OS_VISTA },

    // any Matrox board
    // Reason: there are no known Matrox boards with proper Direct3D support
    { 0x102B, ALL_DEVICEIDS, NO_VERSION, OS_ALL },

    // any SiS board
    // Reason: there aren't many PS2.0-capable SiS boards and they weren't
    // tested
    { 0x1039, ALL_DEVICEIDS, NO_VERSION, OS_ALL },

    // any S3 board
    // Reason: no available S3 Chrome (the only S3 boards with PS2.0 support)
    // for testing
    { 0x5333, ALL_DEVICEIDS, NO_VERSION, OS_ALL },

    // any S3 board (in VIA motherboards)
    // Reason: These are S3 chips in VIA motherboards
    { 0x1106, ALL_DEVICEIDS, NO_VERSION, OS_ALL },

    // last record must be empty
    { 0x0000, 0x0000, NO_VERSION, OS_ALL }
};

#endif // D3DBADHARDWARE_H
