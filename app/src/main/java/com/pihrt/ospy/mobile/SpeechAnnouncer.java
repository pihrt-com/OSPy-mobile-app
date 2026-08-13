package com.pihrt.ospy.mobile;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/** Process-wide, leak-free Text-to-Speech queue for localized OSPy events. */
final class SpeechAnnouncer {
    static final String STATUS_DISABLED = "disabled";
    static final String STATUS_CHECKING = "checking";
    static final String STATUS_READY = "ready";
    static final String STATUS_SPEAKING = "speaking";
    static final String STATUS_SPOKEN = "spoken";
    static final String STATUS_MISSING_DATA = "missing_data";
    static final String STATUS_UNSUPPORTED = "unsupported";
    static final String STATUS_ERROR = "error";
    private static final Object LOCK = new Object();
    private static final int MAX_PENDING = 10;
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static SpeechAnnouncer instance;
    private static volatile String status = STATUS_DISABLED;

    private final Context context;
    private final Queue<String> pending = new ArrayDeque<>();
    private TextToSpeech engine;
    private boolean ready;
    private boolean failed;
    private String languageTag = "";

    private SpeechAnnouncer(Context context) {
        this.context = context.getApplicationContext();
        status = STATUS_CHECKING;
        engine = new TextToSpeech(this.context, this::initialized);
    }

    static void initialize(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (instance == null || instance.failed) {
                if (instance != null && instance.engine != null) {
                    instance.engine.shutdown();
                }
                instance = new SpeechAnnouncer(context);
            } else if (instance.ready) {
                instance.configureLanguage();
            }
        }
    }

    static String status() {
        return status;
    }

    static String languageTag() {
        synchronized (LOCK) {
            return instance == null ? "" : instance.languageTag;
        }
    }

    static void speak(Context context, String text) {
        if (context == null || text == null || text.trim().isEmpty()) return;
        synchronized (LOCK) {
            if (instance == null || instance.failed) initialize(context);
            instance.enqueue(text.trim());
        }
    }

    static void stop() {
        synchronized (LOCK) {
            if (instance == null) return;
            instance.pending.clear();
            if (instance.engine != null) instance.engine.stop();
            status = STATUS_DISABLED;
        }
    }

    private void initialized(int status) {
        synchronized (LOCK) {
            if (status != TextToSpeech.SUCCESS || engine == null) {
                failed = true;
                pending.clear();
                SpeechAnnouncer.status = STATUS_ERROR;
                return;
            }
            engine.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    SpeechAnnouncer.status = STATUS_SPEAKING;
                }

                @Override
                public void onDone(String utteranceId) {
                    SpeechAnnouncer.status = STATUS_SPOKEN;
                }

                @Override
                public void onError(String utteranceId) {
                    SpeechAnnouncer.status = STATUS_ERROR;
                }
            });
            if (!configureLanguage()) return;
            ready = true;
            SpeechAnnouncer.status = STATUS_READY;
            while (!pending.isEmpty()) speakNow(pending.remove());
        }
    }

    private boolean configureLanguage() {
        Locale locale = Build.VERSION.SDK_INT >= 24
                ? context.getResources().getConfiguration().getLocales().get(0)
                : context.getResources().getConfiguration().locale;
        languageTag = locale.toLanguageTag();
        int language = engine.setLanguage(locale);
        if (language == TextToSpeech.LANG_MISSING_DATA) {
            failed = true;
            ready = false;
            pending.clear();
            status = STATUS_MISSING_DATA;
            return false;
        }
        if (language == TextToSpeech.LANG_NOT_SUPPORTED) {
            failed = true;
            ready = false;
            pending.clear();
            status = STATUS_UNSUPPORTED;
            return false;
        }
        return true;
    }

    private void enqueue(String text) {
        if (failed) return;
        if (ready) {
            speakNow(text);
            return;
        }
        while (pending.size() >= MAX_PENDING) pending.remove();
        pending.add(text);
    }

    private void speakNow(String text) {
        int result = engine.speak(
                text,
                TextToSpeech.QUEUE_ADD,
                null,
                "ospy-notification-" + NEXT_ID.incrementAndGet());
        if (result == TextToSpeech.ERROR) status = STATUS_ERROR;
    }
}
