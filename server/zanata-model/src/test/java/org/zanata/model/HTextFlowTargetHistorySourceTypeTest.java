package org.zanata.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zanata.rest.dto.TranslationSourceType;

class HTextFlowTargetHistorySourceTypeTest {

    @Test
    void snapshotCarriesMachineTransSourceType() {
        HTextFlowTarget target = new HTextFlowTarget();
        target.setSourceType(TranslationSourceType.MACHINE_TRANS);

        HTextFlowTargetHistory history = new HTextFlowTargetHistory(target);

        assertThat(history.getSourceType())
                .isEqualTo(TranslationSourceType.MACHINE_TRANS);
    }

    @Test
    void snapshotCarriesNonAiSourceType() {
        HTextFlowTarget target = new HTextFlowTarget();
        target.setSourceType(TranslationSourceType.UNKNOWN);

        HTextFlowTargetHistory history = new HTextFlowTargetHistory(target);

        assertThat(history.getSourceType())
                .isNotEqualTo(TranslationSourceType.MACHINE_TRANS);
    }
}
