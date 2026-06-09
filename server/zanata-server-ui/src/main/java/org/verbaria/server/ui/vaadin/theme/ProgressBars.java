package org.verbaria.server.ui.vaadin.theme;

import com.vaadin.flow.component.progressbar.ProgressBar;

/** Shared styling for translation progress bars and percentage labels. */
public final class ProgressBars {

    /** Below this translated percentage the bar/label is red (error). */
    public static final double LOW_THRESHOLD = 50.0;
    /** At or above this percentage the bar/label is green (success);
     *  between the two thresholds it is orange (warning). */
    public static final double HIGH_THRESHOLD = 80.0;

    private ProgressBars() {
    }

    public static ProgressBar translated(double pct) {
        double clamped = clamp(pct);
        ProgressBar bar = new ProgressBar(0.0, 1.0, clamped / 100.0);
        bar.addClassName(barClass(clamped));
        return bar;
    }

    public static String textColorClass(double pct) {
        double clamped = clamp(pct);
        if (clamped < LOW_THRESHOLD) {
            return AuraUtility.TextColor.ERROR;
        }
        if (clamped < HIGH_THRESHOLD) {
            return AuraUtility.TextColor.ORANGE;
        }
        return AuraUtility.TextColor.SUCCESS;
    }

    public static String barClass(double pct) {
        double clamped = clamp(pct);
        if (clamped < LOW_THRESHOLD) {
            return AuraUtility.Accent.RED;
        }
        if (clamped < HIGH_THRESHOLD) {
            return AuraUtility.Accent.ORANGE;
        }
        return AuraUtility.Accent.GREEN;
    }

    private static double clamp(double pct) {
        return Math.max(0.0, Math.min(100.0, pct));
    }
}
