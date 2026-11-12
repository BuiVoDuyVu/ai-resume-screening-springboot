package com.example.cvmatching;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_descriptions")
public class JobDescriptionEntity {
    @Id
    @Column(name = "job_description_id", nullable = false)
    private UUID id;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "required_skills", nullable = false, columnDefinition = "text")
    private List<String> requiredSkills;

    protected JobDescriptionEntity() { }

    JobDescriptionEntity(String description, List<String> requiredSkills) {
        this.id = UUID.randomUUID();
        this.description = description;
        this.requiredSkills = new ArrayList<>(requiredSkills);
    }

    String description() {
        return description;
    }

    List<String> requiredSkills() {
        return requiredSkills;
    }
}
