package lab.hr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrganizationAssignment(Long expectedOrganizationId, long organizationId) {}
