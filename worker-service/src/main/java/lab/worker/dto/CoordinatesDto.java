package lab.worker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

public record CoordinatesDto(
    @NotNull(message = "is required") Long x,
    @NotNull(message = "is required") @Max(value = 714, message = "must be less than or equal to 714")
        Long y) {}
