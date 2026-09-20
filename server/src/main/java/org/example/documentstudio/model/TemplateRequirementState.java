package org.example.documentstudio.model;

import java.util.List;

/** Evaluated mapping state used by the checklist and validator. */
public record TemplateRequirementState(
        TemplateMappingRequirement requirement,
        boolean satisfied,
        List<String> satisfiedBy) {
    public TemplateRequirementState {
        satisfiedBy = satisfiedBy == null ? List.of() : List.copyOf(satisfiedBy);
    }
}
