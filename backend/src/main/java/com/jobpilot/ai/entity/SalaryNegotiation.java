package com.jobpilot.ai.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "salary_negotiations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryNegotiation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "company_name")
    private String companyName;

    private String role;

    @Column(name = "offer_amount")
    private BigDecimal offerAmount;

    @Column(length = 10)
    @Builder.Default
    private String currency = "USD";

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode analysis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "counter_offer")
    private JsonNode counterOffer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
