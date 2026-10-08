package lab.hr.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.Objects;
import lab.hr.client.WorkerClient;
import lab.hr.config.Settings;
import lab.hr.dto.MoveResult;
import lab.hr.dto.OrganizationAssignment;
import lab.hr.dto.WorkerOrganizationDto;
import lab.hr.exception.ApiException;

@ApplicationScoped
public class HrService {
  @Inject private WorkerClient workerClient;

  public HrService() {}

  HrService(WorkerClient workerClient) {
    this.workerClient = workerClient;
  }

  public URI fireLocation(long workerId, String origin) {
    String base =
        origin != null && Settings.corsOrigins().contains(origin)
            ? origin
            : Settings.workerPublicBaseUrl();
    return URI.create(base + "/api/workers/" + workerId + "?status=FIRED");
  }

  public MoveResult move(long workerId, long from, long to) {
    if (from == to) throw ApiException.conflict("Source and target organizations are equal");
    WorkerOrganizationDto result =
        workerClient.assignOrganization(workerId, new OrganizationAssignment(from, to));
    if (result.worker() == null
        || result.worker().id() != workerId
        || !Objects.equals(result.organizationId(), to))
      throw ApiException.badGateway(
          "Worker API returned an unexpected response; check current organization before retrying");
    return new MoveResult(result.worker(), from, to);
  }
}
