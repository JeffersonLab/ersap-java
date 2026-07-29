/*
 * Copyright (c) 2025.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.examples.engines.generic;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * ERSAP source engine that receives fully reassembled EJFAT events from the
 * native C++ receiver ({@code ejfat_receiver_actor} / E2SAR Reassembler) via
 * JNI and hands each event downstream as {@link EngineDataType#BYTES}
 * (transported as a {@link java.nio.ByteBuffer}).
 *
 * <h3>Java &lt;-&gt; native data flow</h3>
 * <ol>
 *   <li>{@link #createReader(Path, JSONObject)} calls
 *       {@link EjfatNativeReceiver#EjfatNativeReceiver(JSONObject)}, which
 *       parses the config and invokes
 *       {@link #nativeCreate(String, String, int, int, int, int, boolean, boolean)}.
 *       The native call constructs the E2SAR {@code Reassembler}, registers the
 *       worker (when the control plane is enabled), and starts the receive
 *       threads. It returns an opaque {@code long} handle.</li>
 *
 *   <li>On every {@link #readEvent(int)} the base class serializes the call
 *       via its internal reader lock, so the native handle is touched by a
 *       single thread at a time from the ERSAP framework side. The receive
 *       threads inside E2SAR run independently and enqueue reassembled events
 *       for pickup.</li>
 *
 *   <li>{@link #nativePoll(long, int)} blocks up to {@code maxWaitMs} for a
 *       reassembled event. On success it returns the exact number of bytes of
 *       the pending envelope ({@code sizeof(double) + event_body}); on timeout
 *       it returns {@code -1}. On error it throws {@link java.io.IOException}
 *       via JNI. The native side keeps the pending payload buffer alive until
 *       {@link #nativeConsume(long, ByteBuffer)} copies it out.</li>
 *
 *   <li>The Java side allocates a fresh direct {@link ByteBuffer} of exactly
 *       the returned size and calls {@link #nativeConsume(long, ByteBuffer)}.
 *       The native side performs a single {@code memcpy} into the direct
 *       buffer address and releases the E2SAR-allocated payload. No copy goes
 *       through the JVM heap.</li>
 *
 *   <li>The buffer is flipped and returned; ERSAP's {@code RawBytesSerializer}
 *       passes it straight through to xMsg without further copies.</li>
 * </ol>
 *
 * <h3>Envelope layout</h3>
 * Matches {@code ejfat_receiver_actor.cpp} exactly, so downstream Java actors
 * see the same bytes whether the upstream source is the C++ ERSAP actor or
 * this Java source engine:
 * <pre>
 *   [ 8 bytes: data_id as double (native byte order) ]
 *   [ N bytes: reassembled event body                ]
 * </pre>
 *
 * <h3>Native resource ownership</h3>
 * The native handle is owned by exactly one {@link EjfatNativeReceiver}
 * instance. {@link #closeReader()} calls {@link EjfatNativeReceiver#close()}
 * which invokes {@link #nativeDestroy(long)} exactly once and nulls the
 * handle, so it is safe to call multiple times. The base class calls
 * {@link #closeReader()} on both {@code reset()} and {@code destroy()}.
 *
 * <h3>Configuration (JSON, passed under the standard file-open action)</h3>
 * <pre>
 *   {
 *     "action":           "open",
 *     "file":             "&lt;dummy placeholder, e.g. ejfat://receiver&gt;",
 *     "ejfat_uri":        "&lt;required&gt;",
 *     "recv_ip":          "0.0.0.0",
 *     "recv_port":        19522,
 *     "recv_threads":     1,
 *     "event_timeout_ms": 500,
 *     "max_wait_ms":      5000,
 *     "with_cp":          false,
 *     "validate_cert":    true
 *   }
 * </pre>
 *
 * <h3>Native library</h3>
 * Requires {@code libEjfatReceiverJni.so} to be resolvable via
 * {@code java.library.path} (or {@code LD_LIBRARY_PATH}). Typical layout after
 * an {@code enable_ersap=true} build of {@code e2sar-utils}:
 * {@code ${ERSAP_HOME}/lib/libEjfatReceiverJni.so}.
 */
public class EjfatReceiverSource
        extends AbstractEventReaderService<EjfatReceiverSource.EjfatNativeReceiver> {

    // -----------------------------------------------------------------------
    // Native library
    // -----------------------------------------------------------------------

    /** Base name of the JNI shared library (loaded as {@code libEjfatReceiverJni.so}). */
    public static final String NATIVE_LIB = "EjfatReceiverJni";

    static {
        System.loadLibrary(NATIVE_LIB);
    }

    // Native method declarations. See ejfat_receiver_jni.cpp for the bridge.
    private static native long nativeCreate(String ejfatUri,
                                            String recvIp,
                                            int recvPort,
                                            int recvThreads,
                                            int eventTimeoutMs,
                                            int maxWaitMs,
                                            boolean withCp,
                                            boolean validateCert);

    /** @return payload size in bytes (>= sizeof(double)), or {@code -1} on timeout. */
    private static native int nativePoll(long handle, int maxWaitMs);

    /** Copies the pending payload (from the last successful {@link #nativePoll}) into {@code dst}
     *  and releases the native buffer. {@code dst} MUST be a direct ByteBuffer with
     *  {@code remaining() &gt;= size} returned by the poll. */
    private static native void nativeConsume(long handle, ByteBuffer dst);

    /** Returns the E2SAR {@code EventNum_t} of the last consumed event. */
    private static native long nativeLastEventNum(long handle);

    /** Returns the raw {@code data_id} (uint16) of the last consumed event. */
    private static native int nativeLastDataId(long handle);

    /** Releases the native receiver: deregister worker, stop threads, free the handle. */
    private static native void nativeDestroy(long handle);


    // -----------------------------------------------------------------------
    // Reader lifecycle (delegated to base class serialization)
    // -----------------------------------------------------------------------

    @Override
    protected EjfatNativeReceiver createReader(Path file, JSONObject opts)
            throws EventReaderException {
        try {
            return new EjfatNativeReceiver(opts);
        } catch (RuntimeException e) {
            throw new EventReaderException("failed to create EJFAT native receiver", e);
        }
    }

    @Override
    protected void closeReader() {
        if (reader != null) {
            reader.close();
        }
    }

    /**
     * EJFAT is an unbounded live stream; report {@link Integer#MAX_VALUE} to the
     * orchestrator so the standard "count" query yields a well-defined answer.
     */
    @Override
    protected int readEventCount() {
        return Integer.MAX_VALUE;
    }

    /**
     * The header (data_id encoded as double) is written by the native side in
     * native byte order; declare the same so downstream deserializers agree.
     */
    @Override
    protected ByteOrder readByteOrder() {
        return ByteOrder.nativeOrder();
    }

    /**
     * Blocks until the next reassembled event is available (or the configured
     * {@code max_wait_ms} elapses). Returns a direct {@link ByteBuffer} with
     * position=0 and limit=payloadSize, ready to be handed to the framework.
     * The {@code eventNumber} argument is ignored — a live stream has no
     * seekable index; the framework uses it purely as a communication id.
     */
    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        try {
            return reader.nextEvent();
        } catch (RuntimeException e) {
            throw new EventReaderException("EJFAT native receive failed", e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.BYTES;
    }


    // -----------------------------------------------------------------------
    // Native receiver wrapper (thin Java-side owner of the JNI handle)
    // -----------------------------------------------------------------------

    /**
     * Holds the opaque handle returned by {@link #nativeCreate} and drives the
     * two-call poll/consume protocol. Thread-safety: {@link #nextEvent()} may
     * only be called from the single thread that holds the base class's
     * reader lock, matching the way {@link AbstractEventReaderService}
     * invokes {@code readEvent}.
     */
    static final class EjfatNativeReceiver implements AutoCloseable {

        // Config keys mirror the JSON accepted by the C++ ERSAP actor.
        private static final String KEY_URI            = "ejfat_uri";
        private static final String KEY_RECV_IP        = "recv_ip";
        private static final String KEY_RECV_PORT      = "recv_port";
        private static final String KEY_RECV_THREADS   = "recv_threads";
        private static final String KEY_EVT_TIMEOUT_MS = "event_timeout_ms";
        private static final String KEY_MAX_WAIT_MS    = "max_wait_ms";
        private static final String KEY_WITH_CP        = "with_cp";
        private static final String KEY_VALIDATE_CERT  = "validate_cert";

        private volatile long handle;      // 0 => released
        private final int maxWaitMs;

        EjfatNativeReceiver(JSONObject cfg) {
            if (!cfg.has(KEY_URI)) {
                throw new IllegalArgumentException(
                        "EjfatReceiverSource: required config key '" + KEY_URI + "' is missing");
            }
            String uri           = cfg.getString(KEY_URI);
            String recvIp        = cfg.optString(KEY_RECV_IP, "0.0.0.0");
            int    recvPort      = cfg.optInt(KEY_RECV_PORT, 19522);
            int    recvThreads   = cfg.optInt(KEY_RECV_THREADS, 1);
            int    eventTimeout  = cfg.optInt(KEY_EVT_TIMEOUT_MS, 500);
            int    maxWait       = cfg.optInt(KEY_MAX_WAIT_MS, 5000);
            boolean withCp       = cfg.optBoolean(KEY_WITH_CP, false);
            boolean validateCert = cfg.optBoolean(KEY_VALIDATE_CERT, true);

            if (eventTimeout <= 0) {
                throw new IllegalArgumentException(KEY_EVT_TIMEOUT_MS + " must be > 0");
            }
            if (maxWait <= 0) {
                throw new IllegalArgumentException(KEY_MAX_WAIT_MS + " must be > 0");
            }

            this.maxWaitMs = maxWait;
            this.handle = nativeCreate(uri, recvIp, recvPort, recvThreads,
                                       eventTimeout, maxWait, withCp, validateCert);
            if (this.handle == 0L) {
                throw new IllegalStateException(
                        "EjfatReceiverSource: nativeCreate returned NULL handle");
            }
        }

        /**
         * Pulls the next reassembled event. Returns a direct {@link ByteBuffer}
         * on success, or {@code null} if no event arrived within
         * {@code max_wait_ms} (the orchestrator will typically re-request).
         *
         * <p>The buffer is freshly allocated per call so it can be handed off
         * to xMsg without lifetime concerns; the single copy is the JNI-side
         * {@code memcpy} from the E2SAR-owned buffer.
         */
        ByteBuffer nextEvent() {
            long h = handle;
            if (h == 0L) {
                throw new IllegalStateException("EJFAT receiver handle is closed");
            }
            int size = nativePoll(h, maxWaitMs);
            if (size < 0) {
                return null;
            }
            ByteBuffer buf = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
            nativeConsume(h, buf);
            buf.position(0).limit(size);
            return buf;
        }

        long lastEventNumber() {
            long h = handle;
            return (h == 0L) ? -1L : nativeLastEventNum(h);
        }

        int lastDataId() {
            long h = handle;
            return (h == 0L) ? -1 : nativeLastDataId(h);
        }

        /** Idempotent: safe to call multiple times. */
        @Override
        public synchronized void close() {
            long h = handle;
            if (h != 0L) {
                handle = 0L;
                nativeDestroy(h);
            }
        }
    }
}
