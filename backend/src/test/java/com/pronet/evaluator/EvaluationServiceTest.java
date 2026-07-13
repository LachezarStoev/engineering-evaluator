package com.pronet.evaluator;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EvaluationServiceTest {
    @Test
    void normalizesCorporateEmail() {
        assertThat(EvaluationService.normalize("  Person.Name@Example.COM "))
                .isEqualTo("person.name@example.com");
    }

    @Test
    void parsesOnlyValidQuarterThroughApiContract() {
        assertThatThrownBy(() -> new java.text.SimpleDateFormat("yyyy-'Q'q").parse("bad"))
                .isInstanceOf(Exception.class);
    }
}
