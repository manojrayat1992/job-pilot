package com.jobpilot.networking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hiring_managers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HiringManager {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_domain")
    private String companyDomain;

    private String email;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    private String function;

    @Column(name = "management_level")
    private String managementLevel;

    private String country;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
