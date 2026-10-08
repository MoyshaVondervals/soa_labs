package lab.worker.dto;

import jakarta.validation.constraints.NotNull;

public record LocationDto(
    @NotNull(message = "is required") Float x,
    @NotNull(message = "is required") Double y,
    @NotNull(message = "is required") Integer z,
    String name) {}
