/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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


/*
 * FUNCTION
 *      mlib_ImageAffine_u8_1ch_bl
 *      mlib_ImageAffine_u8_2ch_bl
 *      mlib_ImageAffine_u8_3ch_bl
 *      mlib_ImageAffine_u8_4ch_bl
 *        - image affine transformation with Bilinear filtering
 * SYNOPSIS
 *      mlib_status mlib_ImageAffine_u8_?ch_bl(mlib_s32 *leftEdges,
 *                                             mlib_s32 *rightEdges,
 *                                             mlib_s32 *xStarts,
 *                                             mlib_s32 *yStarts,
 *                                             mlib_s32 *sides,
 *                                             mlib_u8  *dstData,
 *                                             mlib_u8  **lineAddr,
 *                                             mlib_s32 dstYStride,
 *                                             mlib_s32 is_affine,
 *                                             mlib_s32 srcYStride)
 *
 * ARGUMENTS
 *      leftEdges  array[dstHeight] of xLeft coordinates
 *      RightEdges array[dstHeight] of xRight coordinates
 *      xStarts    array[dstHeight] of xStart * 65536 coordinates
 *      yStarts    array[dstHeight] of yStart * 65536 coordinates
 *      sides      output array[4]. sides[0] is yStart, sides[1] is yFinish,
 *                 sides[2] is dx * 65536, sides[3] is dy * 65536
 *      dstData    pointer to the first pixel on (yStart - 1) line
 *      lineAddr   array[srcHeight] of pointers to the first pixel on
 *                 the corresponding lines
 *      dstYStride stride of destination image
 *      is_affine  indicator (Affine - GridWarp)
 *      srcYStride stride of source image
 *
 * DESCRIPTION
 *      The functions step along the lines from xLeft to xRight and apply
 *      the bilinear filtering.
 *
 */

#include "mlib_ImageAffine.h"

/***************************************************************/
#define DTYPE  mlib_u8
#define FTYPE  mlib_f32

/***************************************************************/
#define TTYPE    mlib_f32
#define I2F(x)   mlib_U82F32[x]
#define ROUND(x) ((x) + 0.5f)

#define FUN_NAME(CHAN) mlib_ImageAffine_u8_##CHAN##_bl

/***************************************************************/
#ifdef __sparc /* for SPARC, using floating-point multiplies is faster */

/***************************************************************/
#define GET_POINTERS(ind)                                       \
  fdx = (FTYPE)(X & MLIB_MASK) * scale;                         \
  fdy = (FTYPE)(Y & MLIB_MASK) * scale;                         \
  ySrc = MLIB_POINTER_SHIFT(Y);  Y += dY;                       \
  xSrc = X >> MLIB_SHIFT;  X += dX;                             \
  srcPixelPtr = MLIB_POINTER_GET(lineAddr, ySrc) + ind * xSrc;  \
  srcPixelPtr2 = (DTYPE *)((mlib_u8 *)srcPixelPtr + srcYStride)

/***************************************************************/
#define COUNT(ind)                                              \
  pix0_##ind = a00_##ind + fdy * (a10_##ind - a00_##ind);       \
  pix1_##ind = a01_##ind + fdy * (a11_##ind - a01_##ind);       \
  res##ind = ROUND(pix0_##ind + fdx * (pix1_##ind - pix0_##ind))

/***************************************************************/
#define LOAD(ind, ind1, ind2)                                   \
  a00_##ind = I2F(srcPixelPtr[ind1]);                           \
  a01_##ind = I2F(srcPixelPtr[ind2]);                           \
  a10_##ind = I2F(srcPixelPtr2[ind1]);                          \
  a11_##ind = I2F(srcPixelPtr2[ind2])

/***************************************************************/
mlib_status FUN_NAME(1ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;
  FTYPE scale = (FTYPE) 1.0 / MLIB_PREC;

  for (j = yStart; j <= yFinish; j++) {
    FTYPE fdx, fdy;
    TTYPE a00_0, a01_0, a10_0, a11_0;
    FTYPE pix0_0, pix1_0, res0;

    CLIP(1);
    dstLineEnd = (DTYPE *) dstData + xRight;

    GET_POINTERS(1);
    LOAD(0, 0, 1);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr++) {
      COUNT(0);
      GET_POINTERS(1);
      LOAD(0, 0, 1);
      dstPixelPtr[0] = (DTYPE) res0;
    }

    COUNT(0);
    dstPixelPtr[0] = (DTYPE) res0;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(2ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;
  FTYPE scale = (FTYPE) 1.0 / MLIB_PREC;

  for (j = yStart; j <= yFinish; j++) {
    FTYPE fdx, fdy;
    TTYPE a00_0, a01_0, a10_0, a11_0;
    TTYPE a00_1, a01_1, a10_1, a11_1;
    FTYPE pix0_0, pix1_0, res0;
    FTYPE pix0_1, pix1_1, res1;

    CLIP(2);
    dstLineEnd = (DTYPE *) dstData + 2 * xRight;

    GET_POINTERS(2);
    LOAD(0, 0, 2);
    LOAD(1, 1, 3);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr += 2) {
      COUNT(0);
      COUNT(1);
      GET_POINTERS(2);
      LOAD(0, 0, 2);
      LOAD(1, 1, 3);
      dstPixelPtr[0] = (DTYPE) res0;
      dstPixelPtr[1] = (DTYPE) res1;
    }

    COUNT(0);
    COUNT(1);
    dstPixelPtr[0] = (DTYPE) res0;
    dstPixelPtr[1] = (DTYPE) res1;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(3ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;
  FTYPE scale = (FTYPE) 1.0 / MLIB_PREC;

  for (j = yStart; j <= yFinish; j++) {
    FTYPE fdx, fdy;
    FTYPE a00_0, a01_0, a10_0, a11_0;
    FTYPE a00_1, a01_1, a10_1, a11_1;
    FTYPE a00_2, a01_2, a10_2, a11_2;
    FTYPE pix0_0, pix1_0, res0;
    FTYPE pix0_1, pix1_1, res1;
    FTYPE pix0_2, pix1_2, res2;

    CLIP(3);
    dstLineEnd = (DTYPE *) dstData + 3 * xRight;

    GET_POINTERS(3);
    LOAD(0, 0, 3);
    LOAD(1, 1, 4);
    LOAD(2, 2, 5);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr += 3) {
      COUNT(0);
      COUNT(1);
      COUNT(2);
      GET_POINTERS(3);
      LOAD(0, 0, 3);
      LOAD(1, 1, 4);
      LOAD(2, 2, 5);
      dstPixelPtr[0] = (DTYPE) res0;
      dstPixelPtr[1] = (DTYPE) res1;
      dstPixelPtr[2] = (DTYPE) res2;
    }

    COUNT(0);
    COUNT(1);
    COUNT(2);
    dstPixelPtr[0] = (DTYPE) res0;
    dstPixelPtr[1] = (DTYPE) res1;
    dstPixelPtr[2] = (DTYPE) res2;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(4ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;
  FTYPE scale = (FTYPE) 1.0 / MLIB_PREC;

  for (j = yStart; j <= yFinish; j++) {
    FTYPE fdx, fdy;
    TTYPE a00_0, a01_0, a10_0, a11_0;
    TTYPE a00_1, a01_1, a10_1, a11_1;
    TTYPE a00_2, a01_2, a10_2, a11_2;
    TTYPE a00_3, a01_3, a10_3, a11_3;
    FTYPE pix0_0, pix1_0, res0;
    FTYPE pix0_1, pix1_1, res1;
    FTYPE pix0_2, pix1_2, res2;
    FTYPE pix0_3, pix1_3, res3;

    CLIP(4);
    dstLineEnd = (DTYPE *) dstData + 4 * xRight;

    GET_POINTERS(4);
    LOAD(0, 0, 4);
    LOAD(1, 1, 5);
    LOAD(2, 2, 6);
    LOAD(3, 3, 7);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr += 4) {
      COUNT(0);
      COUNT(1);
      COUNT(2);
      COUNT(3);
      GET_POINTERS(4);
      LOAD(0, 0, 4);
      LOAD(1, 1, 5);
      LOAD(2, 2, 6);
      LOAD(3, 3, 7);
      dstPixelPtr[0] = (DTYPE) res0;
      dstPixelPtr[1] = (DTYPE) res1;
      dstPixelPtr[2] = (DTYPE) res2;
      dstPixelPtr[3] = (DTYPE) res3;
    }

    COUNT(0);
    COUNT(1);
    COUNT(2);
    COUNT(3);
    dstPixelPtr[0] = (DTYPE) res0;
    dstPixelPtr[1] = (DTYPE) res1;
    dstPixelPtr[2] = (DTYPE) res2;
    dstPixelPtr[3] = (DTYPE) res3;
  }

  return MLIB_SUCCESS;
}

#else       /* for x86, using integer multiplies is faster */

/* for SHORT/USHORT decrease MLIB_SHIFT due to
 * overflow in multiplies like fdy * (a10 - a00)
 */
/*
#undef  MLIB_SHIFT
#define MLIB_SHIFT  15
*/

#define MLIB_ROUND   (1 << (MLIB_SHIFT - 1))

/***************************************************************/
#define GET_POINTERS(ind)                                        \
  fdx = X & MLIB_MASK;                                           \
  fdy = Y & MLIB_MASK;                                           \
  ySrc = MLIB_POINTER_SHIFT(Y);                                  \
  xSrc = X >> MLIB_SHIFT;                                        \
  srcPixelPtr = MLIB_POINTER_GET(lineAddr, ySrc) + ind * xSrc;   \
  srcPixelPtr2 = (DTYPE *)((mlib_u8 *)srcPixelPtr + srcYStride); \
  X += dX;                                                       \
  Y += dY

/***************************************************************/
#define COUNT(ind)                                                                       \
  pix0_##ind = a00_##ind + ((fdy * (a10_##ind - a00_##ind) + MLIB_ROUND) >> MLIB_SHIFT); \
  pix1_##ind = a01_##ind + ((fdy * (a11_##ind - a01_##ind) + MLIB_ROUND) >> MLIB_SHIFT); \
  res##ind = pix0_##ind + ((fdx * (pix1_##ind - pix0_##ind) + MLIB_ROUND) >> MLIB_SHIFT)

/***************************************************************/
#define LOAD(ind, ind1, ind2)                                   \
  a00_##ind = srcPixelPtr[ind1];                                \
  a01_##ind = srcPixelPtr[ind2];                                \
  a10_##ind = srcPixelPtr2[ind1];                               \
  a11_##ind = srcPixelPtr2[ind2]

/***************************************************************/
mlib_status FUN_NAME(1ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;

#if MLIB_SHIFT == 15
  dX = (dX + 1) >> 1;
  dY = (dY + 1) >> 1;
#endif /* MLIB_SHIFT == 15 */

  for (j = yStart; j <= yFinish; j++) {
    mlib_s32 fdx, fdy;
    mlib_s32 a00_0, a01_0, a10_0, a11_0;
    mlib_s32 pix0_0, pix1_0, res0;

    CLIP(1);
    dstLineEnd = (DTYPE *) dstData + xRight;
#if MLIB_SHIFT == 15
    X = X >> 1;
    Y = Y >> 1;
#endif /* MLIB_SHIFT == 15 */

    GET_POINTERS(1);
    LOAD(0, 0, 1);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr++) {
      COUNT(0);
      GET_POINTERS(1);
      LOAD(0, 0, 1);
      dstPixelPtr[0] = (DTYPE) res0;
    }

    COUNT(0);
    dstPixelPtr[0] = (DTYPE) res0;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(2ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;

#if MLIB_SHIFT == 15
  dX = (dX + 1) >> 1;
  dY = (dY + 1) >> 1;
#endif /* MLIB_SHIFT == 15 */

  for (j = yStart; j <= yFinish; j++) {
    mlib_s32 fdx, fdy;
    mlib_s32 a00_0, a01_0, a10_0, a11_0;
    mlib_s32 a00_1, a01_1, a10_1, a11_1;
    mlib_s32 pix0_0, pix1_0, res0;
    mlib_s32 pix0_1, pix1_1, res1;

    CLIP(2);
    dstLineEnd = (DTYPE *) dstData + 2 * xRight;
#if MLIB_SHIFT == 15
    X = X >> 1;
    Y = Y >> 1;
#endif /* MLIB_SHIFT == 15 */

    GET_POINTERS(2);
    LOAD(0, 0, 2);
    LOAD(1, 1, 3);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr += 2) {
      COUNT(0);
      COUNT(1);
      GET_POINTERS(2);
      LOAD(0, 0, 2);
      LOAD(1, 1, 3);
      dstPixelPtr[0] = (DTYPE) res0;
      dstPixelPtr[1] = (DTYPE) res1;
    }

    COUNT(0);
    COUNT(1);
    dstPixelPtr[0] = (DTYPE) res0;
    dstPixelPtr[1] = (DTYPE) res1;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(3ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;

#if MLIB_SHIFT == 15
  dX = (dX + 1) >> 1;
  dY = (dY + 1) >> 1;
#endif /* MLIB_SHIFT == 15 */

  for (j = yStart; j <= yFinish; j++) {
    mlib_s32 fdx, fdy;
    mlib_s32 a00_0, a01_0, a10_0, a11_0;
    mlib_s32 a00_1, a01_1, a10_1, a11_1;
    mlib_s32 a00_2, a01_2, a10_2, a11_2;
    mlib_s32 pix0_0, pix1_0, res0;
    mlib_s32 pix0_1, pix1_1, res1;
    mlib_s32 pix0_2, pix1_2, res2;

    CLIP(3);
    dstLineEnd = (DTYPE *) dstData + 3 * xRight;
#if MLIB_SHIFT == 15
    X = X >> 1;
    Y = Y >> 1;
#endif /* MLIB_SHIFT == 15 */

    GET_POINTERS(3);
    LOAD(0, 0, 3);
    LOAD(1, 1, 4);
    LOAD(2, 2, 5);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr += 3) {
      COUNT(0);
      COUNT(1);
      COUNT(2);
      GET_POINTERS(3);
      LOAD(0, 0, 3);
      LOAD(1, 1, 4);
      LOAD(2, 2, 5);
      dstPixelPtr[0] = (DTYPE) res0;
      dstPixelPtr[1] = (DTYPE) res1;
      dstPixelPtr[2] = (DTYPE) res2;
    }

    COUNT(0);
    COUNT(1);
    COUNT(2);
    dstPixelPtr[0] = (DTYPE) res0;
    dstPixelPtr[1] = (DTYPE) res1;
    dstPixelPtr[2] = (DTYPE) res2;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(4ch)(mlib_affine_param *param)
{
  DECLAREVAR_BL();
  DTYPE *dstLineEnd;
  DTYPE *srcPixelPtr2;

#if MLIB_SHIFT == 15
  dX = (dX + 1) >> 1;
  dY = (dY + 1) >> 1;
#endif /* MLIB_SHIFT == 15 */

  for (j = yStart; j <= yFinish; j++) {
    mlib_s32 fdx, fdy;
    mlib_s32 a00_0, a01_0, a10_0, a11_0;
    mlib_s32 a00_1, a01_1, a10_1, a11_1;
    mlib_s32 a00_2, a01_2, a10_2, a11_2;
    mlib_s32 a00_3, a01_3, a10_3, a11_3;
    mlib_s32 pix0_0, pix1_0, res0;
    mlib_s32 pix0_1, pix1_1, res1;
    mlib_s32 pix0_2, pix1_2, res2;
    mlib_s32 pix0_3, pix1_3, res3;

    CLIP(4);
    dstLineEnd = (DTYPE *) dstData + 4 * xRight;
#if MLIB_SHIFT == 15
    X = X >> 1;
    Y = Y >> 1;
#endif /* MLIB_SHIFT == 15 */

    GET_POINTERS(4);
    LOAD(0, 0, 4);
    LOAD(1, 1, 5);
    LOAD(2, 2, 6);
    LOAD(3, 3, 7);

#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
    for (; dstPixelPtr < dstLineEnd; dstPixelPtr += 4) {
      COUNT(0);
      COUNT(1);
      COUNT(2);
      COUNT(3);
      GET_POINTERS(4);
      LOAD(0, 0, 4);
      LOAD(1, 1, 5);
      LOAD(2, 2, 6);
      LOAD(3, 3, 7);
      dstPixelPtr[0] = (DTYPE) res0;
      dstPixelPtr[1] = (DTYPE) res1;
      dstPixelPtr[2] = (DTYPE) res2;
      dstPixelPtr[3] = (DTYPE) res3;
    }

    COUNT(0);
    COUNT(1);
    COUNT(2);
    COUNT(3);
    dstPixelPtr[0] = (DTYPE) res0;
    dstPixelPtr[1] = (DTYPE) res1;
    dstPixelPtr[2] = (DTYPE) res2;
    dstPixelPtr[3] = (DTYPE) res3;
  }

  return MLIB_SUCCESS;
}

#endif /* __sparc ( for SPARC, using floating-point multiplies is faster ) */

/***************************************************************/
