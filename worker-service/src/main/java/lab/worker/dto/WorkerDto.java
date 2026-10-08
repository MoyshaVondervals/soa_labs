package lab.worker.dto;

import java.time.OffsetDateTime;
import lab.worker.model.Position;
import lab.worker.model.Status;

public record WorkerDto(
    long id,
    String name,
    CoordinatesDto coordinates,
    OffsetDateTime creationDate,
    double salary,
    OffsetDateTime endDate,
    Position position,
    Status status,
    PersonDto person) {}
