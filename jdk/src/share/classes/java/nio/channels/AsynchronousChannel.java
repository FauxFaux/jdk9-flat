/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.channels;

import java.io.IOException;
import java.util.concurrent.Future;  // javadoc

/**
 * A channel that supports asynchronous I/O operations. Asynchronous I/O
 * operations will usually take one of two forms:
 *
 * <ol>
 * <li><pre>{@link Future}&lt;V&gt; <em>operation</em>(<em>...</em>)</pre></li>
 * <li><pre>Future&lt;V&gt; <em>operation</em>(<em>...</em> A attachment, {@link CompletionHandler}&lt;V,? super A&gt handler)</pre></li>
 * </ol>
 *
 * where <i>operation</i> is the name of the I/O operation (read or write for
 * example), <i>V</i> is the result type of the I/O operation, and <i>A</i> is
 * the type of an object attached to the I/O operation to provide context when
 * consuming the result. The attachment is important for cases where a
 * <em>state-less</em> {@code CompletionHandler} is used to consume the result
 * of many I/O operations.
 *
 * <p> In the first form, the methods defined by the {@link Future Future}
 * interface may be used to check if the operation has completed, wait for its
 * completion, and to retrieve the result. In the second form, a {@link
 * CompletionHandler} is invoked to consume the result of the I/O operation when
 * it completes, fails, or is cancelled.
 *
 * <p> A channel that implements this interface is <em>asynchronously
 * closeable</em>: If an I/O operation is outstanding on the channel and the
 * channel's {@link #close close} method is invoked, then the I/O operation
 * fails with the exception {@link AsynchronousCloseException}.
 *
 * <p> Asynchronous channels are safe for use by multiple concurrent threads.
 * Some channel implementations may support concurrent reading and writing, but
 * may not allow more than one read and one write operation to be outstanding at
 * any given time.
 *
 * <h4>Cancellation</h4>
 *
 * <p> The {@code Future} interface defines the {@link Future#cancel cancel}
 * method to cancel execution of a task.
 *
 * <p> Where the {@code cancel} method is invoked with the {@code
 * mayInterruptIfRunning} parameter set to {@code true} then the I/O operation
 * may be interrupted by closing the channel. This will cause any other I/O
 * operations outstanding on the channel to complete with the exception {@link
 * AsynchronousCloseException}.
 *
 * <p> If a {@code CompletionHandler} is specified when initiating an I/O
 * operation, and the {@code cancel} method is invoked to cancel the I/O
 * operation before it completes, then the {@code CompletionHandler}'s {@link
 * CompletionHandler#cancelled cancelled} method is invoked.
 *
 * <p> If an implementation of this interface supports a means to cancel I/O
 * operations, and where cancellation may leave the channel, or the entity to
 * which it is connected, in an inconsistent state, then the channel is put into
 * an implementation specific <em>error state</em> that prevents further
 * attempts to initiate I/O operations on the channel. For example, if a read
 * operation is cancelled but the implementation cannot guarantee that bytes
 * have not been read from the channel then it puts the channel into error state
 * state; further attempts to initiate a {@code read} operation causes an
 * unspecified runtime exception to be thrown.
 *
 * <p> Where the {@code cancel} method is invoked to cancel read or write
 * operations then it recommended that all buffers used in the I/O operations be
 * discarded or care taken to ensure that the buffers are not accessed while the
 * channel remains open.
 *
 *  @since 1.7
 */

public interface AsynchronousChannel
    extends Channel
{
    /**
     * Closes this channel.
     *
     * <p> Any outstanding asynchronous operations upon this channel will
     * complete with the exception {@link AsynchronousCloseException}. After a
     * channel is closed then further attempts to initiate asynchronous I/O
     * operations complete immediately with cause {@link ClosedChannelException}.
     *
     * <p>  This method otherwise behaves exactly as specified by the {@link
     * Channel} interface.
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    @Override
    void close() throws IOException;
}
