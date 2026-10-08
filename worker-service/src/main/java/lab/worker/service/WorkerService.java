package lab.worker.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lab.worker.dto.OrganizationAssignment;
import lab.worker.dto.WorkerDto;
import lab.worker.dto.WorkerInput;
import lab.worker.dto.WorkerOrganizationDto;
import lab.worker.dto.WorkerPage;
import lab.worker.exception.ApiException;
import lab.worker.mapper.WorkerMapper;
import lab.worker.model.Organization;
import lab.worker.model.Position;
import lab.worker.model.Status;
import lab.worker.model.Worker;
import lab.worker.query.WorkerQuery;
import lab.worker.repository.OrganizationRepository;
import lab.worker.repository.WorkerRepository;

@ApplicationScoped
@Transactional
public class WorkerService {
  @Inject private WorkerRepository workers;
  @Inject private OrganizationRepository organizations;
  @Inject private WorkerMapper mapper;

  public WorkerPage findPage(WorkerQuery query, int page, int size) {
    long total = workers.count(query);
    long offset = (page - 1L) * size;
    List<WorkerDto> items =
        offset >= total
            ? List.of()
            : workers.findPage(query, (int) offset, size).stream().map(mapper::toDto).toList();
    return new WorkerPage(items, page, size, total, (int) ((total + size - 1) / size));
  }

  public WorkerDto findById(long id) {
    return mapper.toDto(existing(id, false));
  }

  public WorkerDto create(WorkerInput input) {
    Worker worker = new Worker();
    mapper.apply(input, worker);
    worker.setCreationDate(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS));
    return mapper.toDto(workers.save(worker));
  }

  public WorkerDto replace(long id, WorkerInput input, Status expectedStatus) {
    Worker worker = existing(id, true);
    checkStatus(worker, expectedStatus);
    mapper.apply(input, worker);
    return mapper.toDto(worker);
  }

  public WorkerDto updateStatus(long id, Status status, Status expectedStatus) {
    Worker worker = existing(id, true);
    checkStatus(worker, expectedStatus);
    worker.setStatus(status);
    return mapper.toDto(worker);
  }

  public void delete(long id) {
    workers.delete(existing(id, true));
  }

  public WorkerOrganizationDto findOrganization(long id) {
    return mapper.toOrganizationDto(existing(id, false));
  }

  public WorkerOrganizationDto assignOrganization(long id, OrganizationAssignment assignment) {
    Worker worker = existing(id, true);
    Long current = worker.getOrganization() == null ? null : worker.getOrganization().getId();
    Long expected = assignment.expectedOrganizationId();
    long target = assignment.organizationId();
    if (!Objects.equals(current, expected) || Objects.equals(expected, target))
      throw ApiException.conflict(
          "Current organization differs from expectedOrganizationId or source equals target");
    Organization organization = organization(target);
    if (expected != null) organization(expected);
    worker.setOrganization(organization);
    return mapper.toOrganizationDto(worker);
  }

  public double salarySum() {
    return workers.sumSalary();
  }

  public long countWithPositionGreaterThan(Position position) {
    List<Position> higher =
        Arrays.stream(Position.values()).filter(p -> p.compareTo(position) > 0).toList();
    return workers.countByPositionIn(higher);
  }

  public List<WorkerDto> findByNameContaining(String substring) {
    return workers.findByNameContaining(substring).stream().map(mapper::toDto).toList();
  }

  private Worker existing(long id, boolean forUpdate) {
    return (forUpdate ? workers.findByIdForUpdate(id) : workers.findById(id))
        .orElseThrow(() -> ApiException.gone("Worker with id " + id + " does not exist"));
  }

  private Organization organization(long id) {
    return organizations
        .findById(id)
        .orElseThrow(() -> ApiException.gone("Organization with id " + id + " does not exist"));
  }

  private static void checkStatus(Worker worker, Status expected) {
    if (expected != null && worker.getStatus() != expected)
      throw ApiException.conflict(
          "Current status " + worker.getStatus() + " differs from expectedStatus " + expected);
  }
}
