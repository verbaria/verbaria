package org.zanata.adapter.consulo;

import org.springframework.stereotype.Component;
import org.verbaria.server.ui.DocumentAction;
import org.verbaria.server.ui.TextFlowGateway;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

@Component
public class ConsuloMnemonicAction implements DocumentAction {

    @Override
    public String labelKey() {
        return "translate.action.convertMnemonics";
    }

    @Override
    public String progressKey() {
        return "translate.convertMnemonics.running";
    }

    @Override
    public String resultKey() {
        return "translate.convertMnemonics.done";
    }

    @Override
    public boolean appliesTo(String projectType) {
        return "consulo".equalsIgnoreCase(projectType);
    }

    @Override
    public int run(TextFlowGateway gateway, long documentId) {
        int changed = 0;
        for (long id : gateway.documentTextFlowIds(documentId)) {
            String raw = gateway.sourceText(id);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            ConsuloSubFile sub =
                    gateway.extension(id, ConsuloSubFile.class).orElse(null);
            if (sub != null && sub.getExtension() != null
                    && !sub.getExtension().isEmpty()) {
                continue;
            }
            TextWithMnemonic twm = TextWithMnemonic.parse(raw);
            if (!twm.hasMnemonic()) {
                continue;
            }
            String text = twm.getText();
            int idx = twm.getMnemonicIndex();
            char ch = text.charAt(idx);
            String mnemonic = String.valueOf(Character.toUpperCase(ch));
            Integer mnemonicIndex =
                    isFirstOccurrence(text, ch, idx) ? null : idx + 1;
            ConsuloSubFile updated = sub == null ? new ConsuloSubFile() : sub;
            updated.setMnemonic(mnemonic);
            updated.setMnemonicIndex(mnemonicIndex);
            // One transaction: strip the marker from the source and store the
            // mnemonic together, so neither is lost if the other fails.
            gateway.update(id, text, updated);
            changed++;
        }
        return changed;
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
}
