package lab.worker.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lab.worker.dto.CoordinatesDto;
import lab.worker.dto.LocationDto;
import lab.worker.dto.PersonDto;
import lab.worker.dto.WorkerDto;
import lab.worker.dto.WorkerInput;
import lab.worker.dto.WorkerOrganizationDto;
import lab.worker.model.Coordinates;
import lab.worker.model.Location;
import lab.worker.model.Person;
import lab.worker.model.Worker;

@ApplicationScoped
public class WorkerMapper {
  public WorkerDto toDto(Worker w) {
    return new WorkerDto(
        w.getId(),
        w.getName(),
        new CoordinatesDto(w.getCoordinates().getX(), w.getCoordinates().getY()),
        utc(w.getCreationDate()),
        w.getSalary(),
        utc(w.getEndDate()),
        w.getPosition(),
        w.getStatus(),
        toDto(w.getPerson()));
  }

  public WorkerOrganizationDto toOrganizationDto(Worker w) {
    Long organizationId = w.getOrganization() == null ? null : w.getOrganization().getId();
    return new WorkerOrganizationDto(toDto(w), organizationId);
  }

  public void apply(WorkerInput input, Worker w) {
    w.setName(input.name());
    w.setCoordinates(new Coordinates(input.coordinates().x(), input.coordinates().y()));
    w.setSalary(input.salary());
    w.setEndDate(utc(input.endDate()));
    w.setPosition(input.position());
    w.setStatus(input.status());
    w.setPerson(toEntity(input.person()));
  }

  private static PersonDto toDto(Person p) {
    Location l = p.getLocation();
    LocationDto location =
        l == null ? null : new LocationDto(l.getX(), l.getY(), l.getZ(), l.getName());
    return new PersonDto(
        p.getPassportID(), p.getEyeColor(), p.getHairColor(), p.getNationality(), location);
  }

  private static Person toEntity(PersonDto p) {
    LocationDto l = p.location();
    Location location = l == null ? null : new Location(l.x(), l.y(), l.z(), l.name());
    return new Person(p.passportID(), p.eyeColor(), p.hairColor(), p.nationality(), location);
  }

  private static OffsetDateTime utc(OffsetDateTime value) {
    return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
