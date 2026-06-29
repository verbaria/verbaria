package org.zanata.adapter.consulo;

import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.springframework.context.annotation.Conditional;
import org.verbaria.server.ui.TextFlowGateway;
import org.verbaria.server.ui.TextFlowMetadataContext;
import org.verbaria.server.ui.TextFlowMetadataProvider;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

@org.springframework.stereotype.Component
@Conditional(VaadinPresent.class)
public class ConsuloMetadataProvider implements TextFlowMetadataProvider {

    @Override
    public boolean appliesTo(TextFlowMetadataContext context) {
        return "consulo".equalsIgnoreCase(context.snapshot().projectType());
    }

    @Override
    public Component editor(TextFlowMetadataContext context) {
        TextFlowGateway gateway = context.gateway();
        long id = context.textFlowId();
        boolean editable = context.canEdit();
        ConsuloSubFile sub =
                context.snapshot().extension(ConsuloSubFile.class).orElse(null);
        String text = context.snapshot().sourceText();
        boolean rawFile = sub != null && isRawFile(sub);

        FormLayout form = new FormLayout();
        form.setWidthFull();
        form.setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("30em", 2),
                new ResponsiveStep("60em", 4));

        TextField extension = new TextField(t("translate.row.extension"));
        extension.setValue(
                sub == null ? "" : Objects.requireNonNullElse(sub.getExtension(), ""));
        extension.setClearButtonVisible(true);
        extension.addThemeVariants(TextFieldVariant.SMALL);
        extension.setReadOnly(!editable);
        form.add(extension);
        form.setColspan(extension, 1);

        final TextField[] charHolder = { null };
        final IntegerField[] indexHolder = { null };
        TextField calculated = null;

        if (!rawFile) {
            final TextField charField =
                    new TextField(t("translate.row.mnemonic"));
            charField.setValue(sub == null
                    ? "" : Objects.requireNonNullElse(sub.getMnemonic(), ""));
            charField.setMaxLength(1);
            charField.setClearButtonVisible(true);
            charField.setValueChangeMode(ValueChangeMode.EAGER);
            charField.addThemeVariants(TextFieldVariant.SMALL);
            charField.setReadOnly(!editable);

            final IntegerField indexField =
                    new IntegerField(t("translate.row.mnemonicIndex"));
            indexField.setValue(sub == null ? null : sub.getMnemonicIndex());
            indexField.setMin(1);
            if (text != null && !text.isEmpty()) {
                indexField.setMax(text.length());
            }
            indexField.setClearButtonVisible(true);
            indexField.setValueChangeMode(ValueChangeMode.EAGER);
            indexField.addThemeVariants(TextFieldVariant.SMALL);
            indexField.setReadOnly(!editable);

            final TextField preview =
                    new TextField(t("translate.row.mnemonicCalculated"));
            preview.addThemeVariants(TextFieldVariant.SMALL);
            preview.setReadOnly(true);

            Runnable recompute = () -> preview.setValue(
                    preview(text, charField.getValue(), indexField.getValue()));
            // The char and index are two complementary ways to point at the
            // mnemonic: typing a character uses its first occurrence, while an
            // index pins an exact position. Editing one resets the other.
            charField.addValueChangeListener(e -> {
                if (e.isFromClient()) {
                    indexField.clear();
                    recompute.run();
                }
            });
            indexField.addValueChangeListener(e -> {
                if (e.isFromClient()) {
                    charField.setValue(derivedChar(text, indexField.getValue()));
                    recompute.run();
                }
            });
            recompute.run();

            charHolder[0] = charField;
            indexHolder[0] = indexField;
            calculated = preview;

            form.add(charField);
            form.setColspan(charField, 1);
            form.add(indexField);
            form.setColspan(indexField, 1);
        }

        if (editable) {
            Button save = new Button(t("translate.row.saveMetadata"),
                    e -> persist(() -> {
                        ConsuloSubFile updated =
                                gateway.extension(id, ConsuloSubFile.class)
                                        .orElseGet(ConsuloSubFile::new);
                        applyExtension(updated, extension.getValue());
                        if (indexHolder[0] != null) {
                            applyMnemonic(updated, text,
                                    charHolder[0].getValue(),
                                    indexHolder[0].getValue());
                        }
                        gateway.putExtension(id, updated);
                    }));
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY,
                    ButtonVariant.LUMO_SMALL);
            form.add(save);
            form.setColspan(save, 1);
        }

        if (calculated != null) {
            form.add(calculated);
            form.setColspan(calculated, 4);
        }

        Div container = new Div(form);
        container.setWidthFull();
        return container;
    }

    private void persist(Runnable op) {
        try {
            op.run();
            Notification.show(t("translate.row.metadataSaved"), 2000,
                    Position.BOTTOM_START);
        } catch (RuntimeException ex) {
            Notification.show(
                    t("translate.row.metadataSaveFailed", ex.getMessage()), 4000,
                    Position.MIDDLE);
        }
    }

    private static void applyExtension(ConsuloSubFile sub, String value) {
        String ext = normalizeExt(value);
        sub.setExtension(ext.isEmpty() ? null : ext);
    }

    private static void applyMnemonic(ConsuloSubFile sub, String text,
            String charValue, Integer index) {
        String ch = charValue == null || charValue.isBlank() ? null
                : String.valueOf(Character.toUpperCase(charValue.trim().charAt(0)));
        if (index != null && text != null && index >= 1
                && index <= text.length()) {
            char positional = text.charAt(index - 1);
            sub.setMnemonic(String.valueOf(Character.toUpperCase(positional)));
            sub.setMnemonicIndex(
                    isFirstOccurrence(text, positional, index - 1) ? null : index);
        } else if (ch != null) {
            sub.setMnemonic(ch);
            sub.setMnemonicIndex(null);
        } else {
            sub.setMnemonic(null);
            sub.setMnemonicIndex(null);
        }
    }

    private static String preview(String text, String charValue, Integer index) {
        if (text == null) {
            return "";
        }
        if (index != null && index >= 1 && index <= text.length()) {
            return markAt(text, index - 1);
        }
        String ch = charValue == null ? "" : charValue.trim();
        if (!ch.isEmpty()) {
            int pos = indexOfIgnoreCase(text, ch.charAt(0));
            return pos < 0
                    ? text + " [&" + Character.toUpperCase(ch.charAt(0)) + "]"
                    : markAt(text, pos);
        }
        return text;
    }

    private static String derivedChar(String text, Integer index) {
        if (text == null || index == null || index < 1 || index > text.length()) {
            return "";
        }
        return String.valueOf(Character.toUpperCase(text.charAt(index - 1)));
    }

    private static String markAt(String text, int pos) {
        return text.substring(0, pos) + "&" + text.substring(pos);
    }

    private static boolean isFirstOccurrence(String text, char ch, int idx) {
        char lower = Character.toLowerCase(ch);
        for (int i = 0; i < idx; i++) {
            if (Character.toLowerCase(text.charAt(i)) == lower) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfIgnoreCase(String text, char ch) {
        char lower = Character.toLowerCase(ch);
        for (int i = 0; i < text.length(); i++) {
            if (Character.toLowerCase(text.charAt(i)) == lower) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeExt(String value) {
        String ext = value == null ? "" : value.trim();
        return ext.startsWith(".") ? ext.substring(1) : ext;
    }

    private static boolean isRawFile(ConsuloSubFile sub) {
        return sub.getExtension() != null && !sub.getExtension().isEmpty();
    }

    private static String t(String key, Object... args) {
        return UI.getCurrent().getTranslation(key, args);
    }
}
