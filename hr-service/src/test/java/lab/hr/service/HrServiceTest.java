package lab.hr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import lab.hr.client.WorkerClient;
import lab.hr.dto.MoveResult;
import lab.hr.dto.OrganizationAssignment;
import lab.hr.dto.WorkerDto;
import lab.hr.dto.WorkerOrganizationDto;
import lab.hr.exception.ApiException;
import org.junit.jupiter.api.Test;

class HrServiceTest {
  private static WorkerDto worker(long id) {
    return new WorkerDto(
        id,
        "Ivan",
        new WorkerDto.Coordinates(1, 2),
        "2026-01-01T00:00:00Z",
        100,
        null,
        "BAKER",
        "HIRED",
        new WorkerDto.Person(null, "BROWN", "RED", null, null));
  }

  private static WorkerClient answering(WorkerOrganizationDto result) {
    return new WorkerClient() {
      @Override
      public WorkerOrganizationDto assignOrganization(long id, OrganizationAssignment a) {
        assertEquals(new OrganizationAssignment(10L, 20L), a);
        return result;
      }
    };
  }

  @Test
  void moveReturnsWorkerAndOrganizations() {
    HrService service = new HrService(answering(new WorkerOrganizationDto(worker(7), 20L)));
    MoveResult result = service.move(7, 10, 20);
    assertEquals(new MoveResult(worker(7), 10, 20), result);
  }

  @Test
  void moveToSameOrganizationIsConflictWithoutCallingWorker() {
    HrService service = new HrService(answering(null));
    assertEquals(409, assertThrows(ApiException.class, () -> service.move(7, 10, 10)).getStatus());
  }

  @Test
  void unexpectedWorkerAnswerIsBadGateway() {
    HrService service = new HrService(answering(new WorkerOrganizationDto(worker(8), 20L)));
    assertEquals(502, assertThrows(ApiException.class, () -> service.move(7, 10, 20)).getStatus());
  }

  @Test
  void fireRedirectsToWorkerStatusPatch() {
    HrService service = new HrService(answering(null));
    assertEquals(
        URI.create("https://localhost:8543/api/workers/5?status=FIRED"),
        service.fireLocation(5, null));
  }
}
