package com.pronet.evaluator;

import com.pronet.evaluator.domain.*;
import com.pronet.evaluator.repository.CriterionRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class CriteriaCatalogService {
    private final CriterionRepository criteria;

    List<Criterion> publishedFor(Employee employee, String levelCode, int version) {
        return criteria
                .findByLevelCodeAndVersionAndStatusOrderByCode(
                        levelCode, version, ConfigStatus.PUBLISHED)
                .stream()
                .filter(criterion -> appliesTo(criterion, employee))
                .toList();
    }

    private static boolean appliesTo(Criterion criterion, Employee employee) {
        return switch (criterion.getScope()) {
            case COMMON -> true;
            case TRACK ->
                    criterion.getTrackCode() != null
                            && criterion.getTrackCode().equalsIgnoreCase(employee.getTrackCode());
            case TEAM ->
                    criterion.getTeamKey() != null
                            && criterion.getTeamKey().equalsIgnoreCase(employee.getTeam());
        };
    }
}
