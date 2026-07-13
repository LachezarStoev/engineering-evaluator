package com.pronet.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.pronet.evaluator.domain.Employee;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiControllerLevelSelectionTest {
    @Test
    void usesAssignedLevelBeforeAProvisionalEvidenceFit() {
        var employee = new Employee();
        employee.setCurrentLevelCode("MID_I");

        assertThat(
                        ApiController.selectReportLevel(
                                employee,
                                List.of(fit("JUNIOR_II", 2, 82, false), fit("MID_I", 3, 79, false)),
                                2))
                .isEqualTo("MID_I");
    }

    @Test
    void usesHighestProvisionalEvidenceFitWhenLevelIsNotAssigned() {
        var employee = new Employee();

        assertThat(
                        ApiController.selectReportLevel(
                                employee,
                                List.of(fit("JUNIOR_II", 2, 82, false), fit("MID_I", 3, 79, false)),
                                2))
                .isEqualTo("JUNIOR_II");
    }

    @Test
    void fullySupportedLevelTakesPrecedence() {
        var employee = new Employee();
        employee.setCurrentLevelCode("MID_I");

        assertThat(
                        ApiController.selectReportLevel(
                                employee,
                                List.of(
                                        fit("JUNIOR_II", 2, 82, false),
                                        fit("SENIOR_I", 5, 100, true)),
                                2))
                .isEqualTo("SENIOR_I");
    }

    private static LevelFitService.LevelFit fit(
            String code, int ordinal, int score, boolean recommended) {
        return new LevelFitService.LevelFit(code, code, ordinal, score, 1, 2, 1, 0, recommended);
    }
}
