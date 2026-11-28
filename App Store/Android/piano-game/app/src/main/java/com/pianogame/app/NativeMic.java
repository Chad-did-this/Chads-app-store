package com.pianogame.app;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;

import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class NativeMic {
    private static final int WINDOW = 4096;
    private static final int[] SOURCES = {
        MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.DEFAULT,
        MediaRecorder.AudioSource.VOICE_RECOGNITION
    };
    private static final int[] RATES = {48000, 44100, 32000, 22050, 16000};

    interface Recorder {
        boolean initialized();
        void start();
        boolean recording();
        int sampleRate();
        int read(short[] block);
        boolean silenced();
        void stop();
        void release();
    }

    interface RecorderFactory {
        Recorder create(int source, int rate);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final RecorderFactory factory;
    private volatile String state = "stopped";
    private volatile String latest = "";
    private Session active;
    private boolean destroyed;

    private static final class Session {
        volatile boolean cancelled;
        Recorder recorder;
    }

    NativeMic() {
        this(AndroidRecorder::create);
    }

    NativeMic(RecorderFactory factory) {
        this.factory = factory;
    }

    String status() {
        return state;
    }

    String frame() {
        return latest;
    }

    synchronized void markStarting() {
        stop();
        if (!destroyed) state = "starting";
    }

    synchronized void start() {
        if (destroyed) return;
        stop();
        Session session = new Session();
        active = session;
        state = "starting";
        worker.execute(() -> capture(session));
    }

    synchronized void fail(String message) {
        stop();
        if (!destroyed) state = message;
    }

    synchronized void stop() {
        Session session = active;
        active = null;
        state = "stopped";
        latest = "";
        if (session == null) return;

        synchronized (session) {
            session.cancelled = true;
            stopRecorder(session.recorder);
        }
    }

    synchronized void destroy() {
        destroyed = true;
        stop();
        worker.shutdownNow();
    }

    private synchronized void publish(Session session, String next, String packet) {
        if (active != session || session.cancelled) return;
        latest = packet == null ? "" : packet;
        state = next;
    }

    private static void stopRecorder(Recorder recorder) {
        if (recorder == null) return;
        try {
            recorder.stop();
        } catch (RuntimeException ignored) {
        }
    }

    private void capture(Session session) {
        for (int source : SOURCES) {
            for (int rate : RATES) {
                if (session.cancelled) return;
                Recorder recorder = null;
                boolean produced = false;

                try {
                    recorder = factory.create(source, rate);
                    if (recorder == null || !recorder.initialized()) continue;

                    synchronized (session) {
                        if (session.cancelled) return;
                        session.recorder = recorder;
                        recorder.start();
                    }
                    if (!recorder.recording()) continue;

                    int actualRate = recorder.sampleRate();
                    short[] block = new short[Math.max(256, actualRate / 50)];
                    short[] ring = new short[WINDOW];
                    byte[] bytes = new byte[WINDOW * 2];
                    int position = 0;
                    int filled = 0;
                    long sequence = 0;

                    while (!session.cancelled) {
                        int count = recorder.read(block);
                        if (count <= 0) break;
                        if (recorder.silenced()) {
                            publish(session, "Android silenced the microphone. Close other recording apps and check microphone privacy.", null);
                            return;
                        }

                        for (int i = 0; i < count; i++) {
                            ring[position] = block[i];
                            position = (position + 1) % WINDOW;
                            if (filled < WINDOW) filled++;
                        }

                        if (filled < WINDOW) continue;

                        for (int i = 0; i < WINDOW; i++) {
                            short value = ring[(position + i) % WINDOW];
                            bytes[2 * i] = (byte) value;
                            bytes[2 * i + 1] = (byte) (value >> 8);
                        }

                        produced = true;
                        String packet = (++sequence) + ":" + actualRate + ":"
                            + Base64.getEncoder().encodeToString(bytes);
                        publish(session, "ready", packet);
                    }

                } catch (SecurityException error) {
                    publish(session, "Permission denied or microphone disabled in Android settings.", null);
                    return;
                } catch (RuntimeException ignored) {
                } finally {
                    synchronized (session) {
                        session.recorder = null;
                        stopRecorder(recorder);
                        if (recorder != null) {
                            try {
                                recorder.release();
                            } catch (RuntimeException ignored) {
                            }
                        }
                    }
                }

                if (produced && !session.cancelled) {
                    publish(session, "Recording interrupted. Close other microphone apps and tap Play again.", null);
                    return;
                }
            }
        }
        publish(session, "Unable to record microphone audio. Check microphone privacy and close other recording apps.", null);
    }

    @SuppressLint("MissingPermission")
    private static final class AndroidRecorder implements Recorder {
        private final AudioRecord recorder;

        private AndroidRecorder(AudioRecord recorder) {
            this.recorder = recorder;
        }

        static Recorder create(int source, int rate) {
            int minimum = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            );
            if (minimum <= 0) return null;
            return new AndroidRecorder(new AudioRecord(
                source, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, WINDOW * 4)
            ));
        }

        @Override public boolean initialized() {
            return recorder.getState() == AudioRecord.STATE_INITIALIZED;
        }

        @Override public void start() {
            recorder.startRecording();
        }

        @Override public boolean recording() {
            return recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
        }

        @Override public int sampleRate() {
            return recorder.getSampleRate();
        }

        @Override public int read(short[] block) {
            return recorder.read(block, 0, block.length, AudioRecord.READ_BLOCKING);
        }

        @Override public boolean silenced() {
            if (Build.VERSION.SDK_INT < 29) return false;
            AudioRecordingConfiguration configuration = recorder.getActiveRecordingConfiguration();
            return configuration != null && configuration.isClientSilenced();
        }

        @Override public void stop() {
            recorder.stop();
        }

        @Override public void release() {
            recorder.release();
        }
    }
}

