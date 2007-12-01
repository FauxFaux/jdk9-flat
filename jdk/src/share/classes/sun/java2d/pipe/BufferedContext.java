/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.pipe;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import sun.java2d.InvalidPipeException;
import sun.java2d.SunGraphics2D;
import sun.java2d.SurfaceData;
import sun.java2d.loops.XORComposite;
import static sun.java2d.pipe.BufferedOpCodes.*;
import static sun.java2d.pipe.BufferedRenderPipe.BYTES_PER_SPAN;

/**
 * Base context class for managing state in a single-threaded rendering
 * environment.  Each state-setting operation (e.g. SET_COLOR) is added to
 * the provided RenderQueue, which will be processed at a later time by a
 * single thread.  Note that the RenderQueue lock must be acquired before
 * calling the validate() method (or any other method in this class).  See
 * the RenderQueue class comments for a sample usage scenario.
 */
public class BufferedContext {

    /*
     * The following flags help the internals of validate() determine
     * the appropriate (meaning correct, or optimal) code path when
     * setting up the current context.  The flags can be bitwise OR'd
     * together as needed.
     */

    /**
     * Indicates that no flags are needed; take all default code paths.
     */
    public static final int NO_CONTEXT_FLAGS = (0 << 0);
    /**
     * Indicates that the source surface (or color value, if it is a simple
     * rendering operation) is opaque (has an alpha value of 1.0).  If this
     * flag is present, it allows us to disable blending in certain
     * situations in order to improve performance.
     */
    public static final int SRC_IS_OPAQUE    = (1 << 0);
    /**
     * Indicates that the operation uses an alpha mask, which may determine
     * the code path that is used when setting up the current paint state.
     */
    public static final int USE_MASK         = (1 << 1);

    protected RenderQueue rq;
    protected RenderBuffer buf;

    /**
     * This is a reference to the most recently validated BufferedContext.  If
     * this value is null, it means that there is no current context.  It is
     * provided here so that validate() only needs to do a quick reference
     * check to see if the BufferedContext passed to that method is the same
     * as the one we've cached here.
     */
    protected static BufferedContext currentContext;

    private SurfaceData     validatedSrcData;
    private SurfaceData     validatedDstData;
    private Region          validatedClip;
    private Composite       validatedComp;
    private Paint           validatedPaint;
    private int             validatedFlags;
    private boolean         xformInUse;

    protected BufferedContext(RenderQueue rq) {
        this.rq = rq;
        this.buf = rq.getBuffer();
    }

    /**
     * Validates the given parameters against the current state for this
     * context.  If this context is not current, it will be made current
     * for the given source and destination surfaces, and the viewport will
     * be updated.  Then each part of the context state (clip, composite,
     * etc.) is checked against the previous value.  If the value has changed
     * since the last call to validate(), it will be updated accordingly.
     *
     * Note that the SunGraphics2D parameter is only used for the purposes
     * of validating a (non-null) Paint parameter.  In all other cases it
     * is safe to pass a null SunGraphics2D and it will be ignored.
     */
    public void validate(SurfaceData srcData, SurfaceData dstData,
                         Region clip, Composite comp,
                         AffineTransform xform,
                         Paint paint, SunGraphics2D sg2d, int flags)
    {
        // assert rq.lock.isHeldByCurrentThread();

        boolean updateClip = (clip != validatedClip);
        boolean updatePaint = (paint != validatedPaint);

        if (!dstData.isValid()) {
            throw new InvalidPipeException("bounds changed");
        }

        if ((currentContext != this) ||
            (srcData != validatedSrcData) ||
            (dstData != validatedDstData))
        {
            if (dstData != validatedDstData) {
                // the clip is dependent on the destination surface, so we
                // need to update it if we have a new destination surface
                updateClip = true;
            }

            if (paint == null) {
                // make sure we update the color state (otherwise, it might
                // not be updated if this is the first time the context
                // is being validated)
                updatePaint = true;
            }

            // update the current source and destination surfaces
            setSurfaces(srcData, dstData);

            currentContext = this;
            validatedSrcData = srcData;
            validatedDstData = dstData;
        }

        // validate clip
        if (updateClip) {
            if (clip != null) {
                setClip(clip);
            } else {
                resetClip();
            }
            validatedClip = clip;
        }

        // validate composite (note that a change in the context flags
        // may require us to update the composite state, even if the
        // composite has not changed)
        if ((comp != validatedComp) || (flags != validatedFlags)) {
            if (comp != null) {
                setComposite(comp, flags);
            } else {
                resetComposite();
            }
            // the paint state is dependent on the composite state, so make
            // sure we update the color below
            updatePaint = true;
            validatedComp = comp;
            validatedFlags = flags;
        }

        // validate transform
        if (xform == null) {
            if (xformInUse) {
                resetTransform();
                xformInUse = false;
            }
        } else {
            setTransform(xform);
            xformInUse = true;
        }

        // validate paint
        if (updatePaint) {
            if (paint != null) {
                BufferedPaints.setPaint(rq, sg2d, paint, flags);
            } else {
                BufferedPaints.resetPaint(rq);
            }
            validatedPaint = paint;
        }

        // mark dstData dirty
        dstData.markDirty();
    }

    /**
     * Invalidates the surfaces associated with this context.  This is
     * useful when the context is no longer needed, and we want to break
     * the chain caused by these surface references.
     */
    public void invalidateSurfaces() {
        validatedSrcData = null;
        validatedDstData = null;
    }

    private void setSurfaces(SurfaceData srcData, SurfaceData dstData) {
        // assert rq.lock.isHeldByCurrentThread();
        rq.ensureCapacityAndAlignment(20, 4);
        buf.putInt(SET_SURFACES);
        buf.putLong(srcData.getNativeOps());
        buf.putLong(dstData.getNativeOps());
    }

    private void resetClip() {
        // assert rq.lock.isHeldByCurrentThread();
        rq.ensureCapacity(4);
        buf.putInt(RESET_CLIP);
    }

    private void setClip(Region clip) {
        // assert rq.lock.isHeldByCurrentThread();
        if (clip.isRectangular()) {
            rq.ensureCapacity(20);
            buf.putInt(SET_RECT_CLIP);
            buf.putInt(clip.getLoX()).putInt(clip.getLoY());
            buf.putInt(clip.getHiX()).putInt(clip.getHiY());
        } else {
            rq.ensureCapacity(28); // so that we have room for at least a span
            buf.putInt(BEGIN_SHAPE_CLIP);
            buf.putInt(SET_SHAPE_CLIP_SPANS);
            // include a placeholder for the span count
            int countIndex = buf.position();
            buf.putInt(0);
            int spanCount = 0;
            int remainingSpans = buf.remaining() / BYTES_PER_SPAN;
            int span[] = new int[4];
            SpanIterator si = clip.getSpanIterator();
            while (si.nextSpan(span)) {
                if (remainingSpans == 0) {
                    buf.putInt(countIndex, spanCount);
                    rq.flushNow();
                    buf.putInt(SET_SHAPE_CLIP_SPANS);
                    countIndex = buf.position();
                    buf.putInt(0);
                    spanCount = 0;
                    remainingSpans = buf.remaining() / BYTES_PER_SPAN;
                }
                buf.putInt(span[0]); // x1
                buf.putInt(span[1]); // y1
                buf.putInt(span[2]); // x2
                buf.putInt(span[3]); // y2
                spanCount++;
                remainingSpans--;
            }
            buf.putInt(countIndex, spanCount);
            rq.ensureCapacity(4);
            buf.putInt(END_SHAPE_CLIP);
        }
    }

    private void resetComposite() {
        // assert rq.lock.isHeldByCurrentThread();
        rq.ensureCapacity(4);
        buf.putInt(RESET_COMPOSITE);
    }

    private void setComposite(Composite comp, int flags) {
        // assert rq.lock.isHeldByCurrentThread();
        if (comp instanceof AlphaComposite) {
            AlphaComposite ac = (AlphaComposite)comp;
            rq.ensureCapacity(16);
            buf.putInt(SET_ALPHA_COMPOSITE);
            buf.putInt(ac.getRule());
            buf.putFloat(ac.getAlpha());
            buf.putInt(flags);
        } else if (comp instanceof XORComposite) {
            int xorPixel = ((XORComposite)comp).getXorPixel();
            rq.ensureCapacity(8);
            buf.putInt(SET_XOR_COMPOSITE);
            buf.putInt(xorPixel);
        } else {
            throw new InternalError("not yet implemented");
        }
    }

    private void resetTransform() {
        // assert rq.lock.isHeldByCurrentThread();
        rq.ensureCapacity(4);
        buf.putInt(RESET_TRANSFORM);
    }

    private void setTransform(AffineTransform xform) {
        // assert rq.lock.isHeldByCurrentThread();
        rq.ensureCapacityAndAlignment(52, 4);
        buf.putInt(SET_TRANSFORM);
        buf.putDouble(xform.getScaleX());
        buf.putDouble(xform.getShearY());
        buf.putDouble(xform.getShearX());
        buf.putDouble(xform.getScaleY());
        buf.putDouble(xform.getTranslateX());
        buf.putDouble(xform.getTranslateY());
    }
}
