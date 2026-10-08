package lab.worker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lab.worker.model.Country;
import lab.worker.model.EyeColor;
import lab.worker.model.HairColor;

public record PersonDto(
    String passportID,
    @NotNull(message = "is required") EyeColor eyeColor,
    @NotNull(message = "is required") HairColor hairColor,
    Country nationality,
    @Valid LocationDto location) {}
