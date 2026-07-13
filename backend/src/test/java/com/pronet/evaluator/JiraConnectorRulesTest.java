package com.pronet.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JiraConnectorRulesTest {
    @Test
    void velocityRequiresDoneResolution() {
        assertThat(JiraConnector.countsForVelocity(Map.of("resolution", Map.of("name", "Done"))))
                .isTrue();
        assertThat(JiraConnector.countsForVelocity(Map.of("resolution", Map.of("name", "done"))))
                .isTrue();
        assertThat(
                        JiraConnector.countsForVelocity(
                                Map.of("resolution", Map.of("name", "Duplicate"))))
                .isFalse();
        assertThat(JiraConnector.countsForVelocity(Map.of())).isFalse();
    }
}
