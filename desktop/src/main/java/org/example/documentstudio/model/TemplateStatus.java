package org.example.documentstudio.model;

/** Lifecycle for Studio templates. Published is intentionally separate from runtime activation. */
public enum TemplateStatus {
    DRAFT, PUBLISHED, ACTIVE, ARCHIVED
}
