package com.kaleeb.wezterm;

import android.content.Context;
import android.view.Choreographer;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

/*
 * WHY(USER-ACCEPTED-V292-SCROLL-2026-08-03): this exact architecture restored
 * the phone behavior the user accepted: a short, slow drag stays under the
 * finger, while a fast release coasts naturally in either direction and can be
 * stopped immediately by the next touch. Keep Android's device-scaled
 * ViewConfiguration thresholds, stock OverScroller spline/default friction,
 * Choreographer pacing, and signed relative-pixel output together.
 *
 * BREAKS IF "SIMPLIFIED": do not replace this with guessed duration/distance
 * thresholds, fixed row bursts, custom decay/friction, absolute scroll offsets,
 * or post-UP replay. Every kinetic frame is local PixelSink movement only; it
 * must never perform tmux, HTTP, cache-fill, or stream-control work. MainActivity
 * owns the exact gesture/window/generation fence and cancels this controller on
 * a new DOWN or owner change, while the live stream remains connected and stages
 * updates independently. The mandatory native-fling/stream, released-hold, and
 * second-DOWN build guards are the publication veto for these invariants.
 */
final class NativeHistoryScrollController implements Choreographer.FrameCallback {
    interface PixelSink { boolean applyRelativePixels(float deltaY, String direction); }
    interface StateSink { void onKineticStateChanged(boolean running); }

    private final OverScroller scroller;
    private final Choreographer choreographer;
    private final PixelSink pixelSink;
    private final StateSink stateSink;
    private final int minimumFlingVelocity;
    private final int maximumFlingVelocity;
    private int lastY;
    private boolean framePosted;
    private String direction = "";

    NativeHistoryScrollController(Context context, PixelSink pixelSink, StateSink stateSink) {
        scroller = new OverScroller(context);
        choreographer = Choreographer.getInstance();
        this.pixelSink = pixelSink;
        this.stateSink = stateSink;
        ViewConfiguration configuration = ViewConfiguration.get(context);
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    void dragBy(float deltaY, String dragDirection) {
        cancel();
        if (Math.abs(deltaY) >= 0.01f) pixelSink.applyRelativePixels(deltaY, dragDirection);
    }

    int maximumFlingVelocityPxPerSecond() {
        return maximumFlingVelocity;
    }

    int minimumFlingVelocityPxPerSecond() {
        return minimumFlingVelocity;
    }

    boolean isFlingVelocity(float velocityPxPerSecond) {
        return Math.abs(velocityPxPerSecond) >= minimumFlingVelocity;
    }

    boolean fling(float releaseVelocityPxPerSecond, String flingDirection) {
        cancel();
        float magnitude = Math.abs(releaseVelocityPxPerSecond);
        if (!isFlingVelocity(magnitude)) return false;
        direction = "lineDown".equals(flingDirection) ? "lineDown" : "lineUp";
        int velocity = Math.round(Math.min(maximumFlingVelocity, magnitude));
        if ("lineDown".equals(direction)) velocity = -velocity;
        lastY = 0;
        // WHY: this is only a relative pixel integrator. The renderer owns the
        // absolute viewport/anchor, so READY prepends never restart native velocity.
        scroller.fling(0, 0, 0, velocity, 0, 0, -1_000_000_000, 1_000_000_000);
        stateSink.onKineticStateChanged(true);
        postNextFrame();
        return true;
    }

    void cancel() {
        if (!scroller.isFinished()) scroller.abortAnimation();
        if (framePosted) {
            choreographer.removeFrameCallback(this);
            framePosted = false;
        }
        direction = "";
        stateSink.onKineticStateChanged(false);
    }

    boolean isRunning() { return !scroller.isFinished(); }

    private void postNextFrame() {
        if (!framePosted) {
            framePosted = true;
            choreographer.postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        framePosted = false;
        if (!scroller.computeScrollOffset()) {
            cancel();
            return;
        }
        int currentY = scroller.getCurrY();
        float deltaY = currentY - lastY;
        lastY = currentY;
        if (Math.abs(deltaY) >= 0.01f && !pixelSink.applyRelativePixels(deltaY, direction)) {
            cancel();
            return;
        }
        if (scroller.isFinished()) cancel(); else postNextFrame();
    }
}
