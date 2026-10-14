package dev.novautils.gui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Tiny queue of short on-screen messages, drawn by the Notifications module. */
public final class Toasts {
    public static final long LIFETIME_MS = 2200;
    private static final int MAX = 4;

    public record Toast(String text, int color, long createdAt) {}

    private static final Deque<Toast> QUEUE = new ArrayDeque<>();

    private Toasts() {}

    public static synchronized void push(String text, int color) {
        QUEUE.addFirst(new Toast(text, color, System.currentTimeMillis()));
        while (QUEUE.size() > MAX) QUEUE.removeLast();
    }

    /** Live toasts, newest first; expired ones are dropped. */
    public static synchronized List<Toast> active() {
        long now = System.currentTimeMillis();
        QUEUE.removeIf(t -> now - t.createdAt() > LIFETIME_MS);
        return new ArrayList<>(QUEUE);
    }
}
