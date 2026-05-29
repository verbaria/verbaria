package org.zanata.spring.vaadin;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.i18n.I18NProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.zanata.spring.vaadin.theme.AuraUtility;

/**
 * Run a long-running background task while showing a small modal progress
 * dialog. The dialog blocks the rest of the UI until the task finishes,
 * keeps a status line + ProgressBar fed from the worker via
 * {@link Handle#update(int, int, String)}, and auto-closes on completion.
 *
 * <p>Usage:
 * <pre>{@code
 *   progressDialogs.run("Translating", handle -> {
 *           for (int i = 0; i < total; i++) {
 *               doWork(i);
 *               handle.update(i + 1, total, "Item " + (i + 1));
 *           }
 *           return savedCount;
 *       })
 *       .thenAccept(saved -> Notification.show("Saved " + saved));
 * }</pre>
 *
 * <p>Both the worker function and the {@code thenAccept} callback may safely
 * touch Vaadin components — the future completes on the UI thread.
 */
@Service
public class ProgressDialogService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ProgressDialogService.class);

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "zanata-progress");
        t.setDaemon(true);
        return t;
    });

    /**
     * I18N provider bean used to resolve translations from the worker thread.
     * {@code UI.getTranslation()} can't be used there: it reaches through
     * {@code VaadinService.getCurrent()} / the session, both {@code null} off
     * the UI thread, so it falls back to the {@code !{key}!} marker. Resolving
     * against this bean with a captured {@link Locale} is thread-independent.
     */
    private final I18NProvider i18n;

    public ProgressDialogService(I18NProvider i18n) {
        this.i18n = i18n;
    }

    /**
     * Run {@code work} on a background thread, blocking the UI with a modal
     * progress dialog. The returned future completes on the UI thread.
     */
    public <T> CompletableFuture<T> run(String title, Function<Handle, T> work) {
        UI ui = UI.getCurrent();
        if (ui == null) throw new IllegalStateException("No current Vaadin UI");

        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(title);
        dlg.setCloseOnEsc(false);
        dlg.setCloseOnOutsideClick(false);
        dlg.setDraggable(false);
        dlg.setResizable(false);
        dlg.setWidth("420px");

        ProgressBar bar = new ProgressBar(0, 1);
        bar.setIndeterminate(true);
        bar.setWidthFull();

        Span status = new Span(ui.getTranslation("progress.starting"));
        status.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);

        Span counter = new Span("");
        counter.getStyle().set("font-variant-numeric", "tabular-nums");
        counter.addClassNames(AuraUtility.FontWeight.SEMIBOLD, AuraUtility.TextColor.BODY);

        HorizontalLayout statusRow = new HorizontalLayout(status, counter);
        statusRow.setWidthFull();
        statusRow.setJustifyContentMode(
                com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout body = new VerticalLayout(bar, statusRow);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidthFull();
        dlg.add(body);
        dlg.open();

        // Capture the locale on the UI thread; the worker uses it (plus the
        // injected provider) to translate without a Vaadin session context.
        Handle handle = new Handle(ui, i18n, ui.getLocale(), bar, status, counter);
        CompletableFuture<T> future = new CompletableFuture<>();

        pool.submit(() -> {
            try {
                T result = work.apply(handle);
                ui.access(() -> {
                    dlg.close();
                    future.complete(result);
                    ui.push();
                });
            } catch (Throwable ex) {
                log.warn("Progress task '{}' failed", title, ex);
                ui.access(() -> {
                    // Switch dialog into "error" state so the user can dismiss it
                    bar.setIndeterminate(false);
                    bar.setValue(bar.getMin());
                    status.setText(ui.getTranslation("progress.failed", ex.getMessage()));
                    status.addClassNames(AuraUtility.TextColor.ERROR);
                    Button close = new Button(ui.getTranslation("progress.close"), e -> dlg.close());
                    close.addThemeVariants(ButtonVariant.TERTIARY);
                    dlg.getFooter().removeAll();
                    dlg.getFooter().add(close);
                    future.completeExceptionally(ex);
                    ui.push();
                });
            }
        });
        return future;
    }

    @Override public void destroy() { pool.shutdownNow(); }

    /**
     * Worker-thread handle for updating the modal dialog. All methods marshal
     * back to the UI thread via {@code ui.access(...)} — safe to call from any
     * thread.
     */
    public static final class Handle {
        private final UI ui;
        private final I18NProvider i18n;
        private final Locale locale;
        private final ProgressBar bar;
        private final Span status;
        private final Span counter;

        Handle(UI ui, I18NProvider i18n, Locale locale,
               ProgressBar bar, Span status, Span counter) {
            this.ui = ui;
            this.i18n = i18n;
            this.locale = locale;
            this.bar = bar;
            this.status = status;
            this.counter = counter;
        }

        /**
         * Translate a key with arguments. Safe to call from the background
         * worker: resolves against the injected {@link I18NProvider} with the
         * locale captured on the UI thread, so it never touches the (null)
         * Vaadin session that {@link UI#getTranslation} would.
         */
        public String t(String key, Object... args) {
            return i18n.getTranslation(key, locale, args);
        }

        public void update(int done, int total, String message) {
            ui.access(() -> {
                if (total > 0) {
                    bar.setIndeterminate(false);
                    bar.setMax(total);
                    bar.setValue(Math.min(done, total));
                    counter.setText(done + " / " + total);
                } else {
                    counter.setText("");
                }
                if (message != null) status.setText(message);
                ui.push();
            });
        }

        public void status(String message) {
            ui.access(() -> {
                status.setText(message);
                ui.push();
            });
        }
    }
}
