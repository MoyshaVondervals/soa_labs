package lab.worker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lab.worker.model.Position;
import lab.worker.model.Status;

public record WorkerInput(
    @NotNull(message = "is required") @Size(min = 1, message = "must not be empty") String name,
    @NotNull(message = "is required") @Valid CoordinatesDto coordinates,
    @NotNull(message = "is required") @Positive(message = "must be greater than 0") Double salary,
    OffsetDateTime endDate,
    @NotNull(message = "is required") Position position,
    @NotNull(message = "is required") Status status,
    @NotNull(message = "is required") @Valid PersonDto person) {}
