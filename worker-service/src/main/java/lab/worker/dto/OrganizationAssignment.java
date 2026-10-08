package lab.worker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrganizationAssignment(
    @Positive(message = "must be greater than 0") Long expectedOrganizationId,
    @NotNull(message = "is required") @Positive(message = "must be greater than 0")
        Long organizationId) {}
