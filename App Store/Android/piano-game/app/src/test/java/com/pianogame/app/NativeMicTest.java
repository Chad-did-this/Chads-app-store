package com.pianogame.app;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

public final class NativeMicTest {
    private NativeMic mic;

    @After public void cleanup() {
        if (mic != null) mic.destroy();
    }

    @Test public void fallsBackAcrossRatesAndReportsTheActualRate() throws Exception {
        List<Integer> rates = Collections.synchronizedList(new ArrayList<>());
        FakeRecorder rejected = new FakeRecorder();
        rejected.initialized = false;
        FakeRecorder working = new FakeRecorder();
        working.feed(1234, 20);

        mic = new NativeMic((source, rate) -> {
            rates.add(rate);
            if (rate == 48000) return rejected;
            if (rate == 44100) throw new IllegalArgumentException();
            return working;
        });
        mic.start();
        await(() -> mic.status().equals("ready"));

        assertEquals(Arrays.asList(48000, 44100, 32000), rates);
        assertTrue(rejected.released.await(1, TimeUnit.SECONDS));
        String[] packet = mic.frame().split(":", 3);
        assertEquals("16000", packet[1]);
        byte[] pcm = Base64.getDecoder().decode(packet[2]);
        assertEquals(8192, pcm.length);
        assertEquals(1234, (pcm[0] & 255) | (pcm[1] << 8));
        mic.stop();
        assertEquals("stopped", mic.status());
        assertEquals("", mic.frame());
        assertTrue(working.released.await(1, TimeUnit.SECONDS));
    }

    @Test public void cancelsRecorderConstructionBeforeItCanStart() throws Exception {
        CountDownLatch constructing = new CountDownLatch(1);
        CountDownLatch finishConstruction = new CountDownLatch(1);
        FakeRecorder recorder = new FakeRecorder();
        mic = new NativeMic((source, rate) -> {
            constructing.countDown();
            try {
                if (!finishConstruction.await(1, TimeUnit.SECONDS)) throw new IllegalStateException();
            } catch (InterruptedException error) {
                throw new IllegalStateException(error);
            }
            return recorder;
        });
        mic.start();
        assertTrue(constructing.await(1, TimeUnit.SECONDS));
        mic.stop();
        finishConstruction.countDown();

        assertTrue(recorder.released.await(1, TimeUnit.SECONDS));
        assertEquals(0, recorder.starts);
        assertEquals("stopped", mic.status());
        assertEquals("", mic.frame());
    }

    @Test public void unsupportedSourceFallsBackToTheNextSource() throws Exception {
        List<Integer> sources = Collections.synchronizedList(new ArrayList<>());
        FakeRecorder working = new FakeRecorder();
        working.feed(4, 20);
        mic = new NativeMic((source, rate) -> {
            sources.add(source);
            return source == 9 ? null : working;
        });
        mic.start();
        await(() -> mic.status().equals("ready"));

        assertEquals(Arrays.asList(9, 9, 9, 9, 9, 1), sources);
        assertEquals(1, working.starts);
    }

    @Test public void newSessionCannotReceiveOldSessionFrames() throws Exception {
        FakeRecorder first = new FakeRecorder();
        first.feed(1, 20);
        FakeRecorder second = new FakeRecorder();
        second.feed(2, 20);
        AtomicInteger created = new AtomicInteger();
        mic = new NativeMic((source, rate) -> created.getAndIncrement() == 0 ? first : second);
        mic.start();
        await(() -> mic.status().equals("ready"));
        mic.start();
        assertTrue(first.released.await(1, TimeUnit.SECONDS));
        await(() -> mic.status().equals("ready"));

        byte[] pcm = Base64.getDecoder().decode(mic.frame().split(":", 3)[2]);
        assertEquals(2, pcm[0]);
        assertEquals(0, pcm[1]);
    }

    @Test public void frameworkSilencingFailsWithoutPublishingFakeReadyFrames() throws Exception {
        FakeRecorder recorder = new FakeRecorder();
        recorder.silenced = true;
        recorder.feed(0, 1);
        mic = new NativeMic((source, rate) -> recorder);
        mic.start();
        await(() -> mic.status().contains("silenced"));

        assertEquals("", mic.frame());
        assertTrue(recorder.released.await(1, TimeUnit.SECONDS));
    }

    @Test public void quietPausesKeepAHealthyMicrophoneListening() throws Exception {
        FakeRecorder recorder = new FakeRecorder();
        recorder.feed(0, 100);
        mic = new NativeMic((source, rate) -> recorder);
        mic.start();
        await(() -> mic.status().equals("ready") && recorder.blocks.isEmpty());

        assertEquals("ready", mic.status());
        byte[] pcm = Base64.getDecoder().decode(mic.frame().split(":", 3)[2]);
        assertArrayEquals(new byte[8192], pcm);
        assertEquals(1, recorder.starts);
    }

    @Test public void readFailureClearsTheLastFrameAndAllowsRetry() throws Exception {
        FakeRecorder recorder = new FakeRecorder();
        recorder.feed(5, 20);
        mic = new NativeMic((source, rate) -> recorder);
        mic.start();
        await(() -> mic.status().equals("ready"));
        recorder.blocks.add(-1);
        await(() -> mic.status().startsWith("Recording interrupted"));
        assertEquals("", mic.frame());

        mic.markStarting();
        assertEquals("starting", mic.status());
    }

    @Test public void deniedPermissionDoesNotContinueTryingAudioSources() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        mic = new NativeMic((source, rate) -> {
            attempts.incrementAndGet();
            throw new SecurityException();
        });
        mic.start();
        await(() -> mic.status().startsWith("Permission denied"));
        assertEquals(1, attempts.get());
        assertEquals("", mic.frame());
    }

    @Test public void destroyedMicrophoneRejectsQueuedStarts() {
        AtomicInteger attempts = new AtomicInteger();
        mic = new NativeMic((source, rate) -> {
            attempts.incrementAndGet();
            return null;
        });
        mic.destroy();
        mic.markStarting();
        mic.start();
        assertEquals(0, attempts.get());
        assertEquals("stopped", mic.status());
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue("Recording worker did not reach the expected state", condition.getAsBoolean());
    }

    private static final class FakeRecorder implements NativeMic.Recorder {
        final LinkedBlockingQueue<Integer> blocks = new LinkedBlockingQueue<>();
        final CountDownLatch released = new CountDownLatch(1);
        boolean initialized = true;
        boolean silenced;
        volatile boolean stopped;
        volatile int starts;

        void feed(int value, int count) {
            for (int i = 0; i < count; i++) blocks.add(value);
        }

        @Override public boolean initialized() { return initialized; }
        @Override public void start() { starts++; }
        @Override public boolean recording() { return true; }
        @Override public int sampleRate() { return 16000; }
        @Override public boolean silenced() { return silenced; }

        @Override public int read(short[] block) {
            try {
                if (stopped) return -1;
                int value = blocks.take();
                if (value == -1) return -1;
                Arrays.fill(block, (short) value);
                return block.length;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }

        @Override public void stop() {
            stopped = true;
            blocks.add(-1);
        }

        @Override public void release() { released.countDown(); }
    }
}
