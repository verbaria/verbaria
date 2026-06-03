package org.verbaria.server.ui.vaadin.theme;

import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.progressbar.ProgressBarVariant;

/** Shared styling for translation progress bars. */
public final class ProgressBars {

    /** Below this translated percentage the bar turns red. */
    public static final double LOW_THRESHOLD = 50.0;

    private ProgressBars() {
    }

    /**
     * A translation progress bar for {@code pct} (0..100): red below
     * {@link #LOW_THRESHOLD}, green at or above. ({@code LUMO_*} here are
     * Vaadin theme-variant enum constants, not CSS variables.)
     */
    public static ProgressBar translated(double pct) {
        double clamped = Math.max(0.0, Math.min(100.0, pct));
        ProgressBar bar = new ProgressBar(0.0, 1.0, clamped / 100.0);
        bar.addThemeVariants(clamped < LOW_THRESHOLD
                ? ProgressBarVariant.LUMO_ERROR
                : ProgressBarVariant.LUMO_SUCCESS);
        return bar;
    }
}
