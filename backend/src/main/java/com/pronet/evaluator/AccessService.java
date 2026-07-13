package com.pronet.evaluator;

import com.pronet.evaluator.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.*;
import org.springframework.stereotype.Component;

@Component("access")
@RequiredArgsConstructor
class AccessService {
    private final EmployeeRepository employees;
    private final EvaluationRepository evaluations;

    public boolean canViewEmployee(String email, Authentication auth) {
        if (auth == null) return false;
        String requester = EvaluationService.normalize(auth.getName());
        if (requester.equals(EvaluationService.normalize(email))) return true;
        if (has(auth, "ENGINEERING_MANAGER")
                || has(auth, "HR")
                || has(auth, "AUDITOR")
                || has(auth, "EVALUATOR_ADMIN")) return true;
        if (has(auth, "TEAM_LEAD")) {
            var e = employees.findByCanonicalEmailIgnoreCase(email);
            return e.isPresent()
                    && requester.equals(EvaluationService.normalize(e.get().getManagerEmail()));
        }
        return false;
    }

    public boolean canViewEvaluation(java.util.UUID id, Authentication auth) {
        var e = evaluations.findById(id);
        if (e.isEmpty()) return false;
        var employee = employees.findById(e.get().getEmployeeId());
        return employee.isPresent() && canViewEmployee(employee.get().getCanonicalEmail(), auth);
    }

    private boolean has(Authentication a, String role) {
        return a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_" + role));
    }
}
