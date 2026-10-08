package lab.worker.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import lab.worker.dto.CountResult;
import lab.worker.dto.OrganizationAssignment;
import lab.worker.dto.SalarySum;
import lab.worker.dto.WorkerDto;
import lab.worker.dto.WorkerInput;
import lab.worker.dto.WorkerOrganizationDto;
import lab.worker.dto.WorkerPage;
import lab.worker.model.Position;
import lab.worker.model.Status;
import lab.worker.query.WorkerQueryParser;
import lab.worker.service.WorkerService;
import lab.worker.validation.BeanValidator;
import lab.worker.validation.JsonParamReader;
import lab.worker.validation.RequestParams;

@Path("/workers")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class WorkerController {
  @Inject private WorkerService service;
  @Inject private BeanValidator validator;

  @GET
  public WorkerPage list(
      @QueryParam("page") String page,
      @QueryParam("size") String size,
      @QueryParam("sort") List<String> sort,
      @QueryParam("filter") List<String> filter) {
    int pageNumber = RequestParams.intInRange(page, "page", 1, 1, Integer.MAX_VALUE);
    int pageSize = RequestParams.intInRange(size, "size", 20, 1, 1000);
    return service.findPage(WorkerQueryParser.parse(filter, sort), pageNumber, pageSize);
  }

  @POST
  public Response create(@QueryParam("worker") String worker, @Context UriInfo uri) {
    WorkerDto created = service.create(workerInput(worker));
    URI location = uri.getAbsolutePathBuilder().path(String.valueOf(created.id())).build();
    return Response.created(location).entity(created).build();
  }

  @GET
  @Path("/{id}")
  public WorkerDto get(@PathParam("id") String id) {
    return service.findById(RequestParams.id(id, "id"));
  }

  @PUT
  @Path("/{id}")
  public WorkerDto replace(
      @PathParam("id") String id,
      @QueryParam("worker") String worker,
      @QueryParam("expectedStatus") String expectedStatus) {
    long workerId = RequestParams.id(id, "id");
    WorkerInput input = workerInput(worker);
    Status expected = RequestParams.optionalEnum(expectedStatus, "expectedStatus", Status.class);
    return service.replace(workerId, input, expected);
  }

  @PATCH
  @Path("/{id}")
  public WorkerDto updateStatus(
      @PathParam("id") String id,
      @QueryParam("status") String status,
      @QueryParam("expectedStatus") String expectedStatus) {
    long workerId = RequestParams.id(id, "id");
    Status newStatus = RequestParams.requiredEnum(status, "status", Status.class);
    Status expected = RequestParams.optionalEnum(expectedStatus, "expectedStatus", Status.class);
    return service.updateStatus(workerId, newStatus, expected);
  }

  @DELETE
  @Path("/{id}")
  public Response delete(@PathParam("id") String id) {
    service.delete(RequestParams.id(id, "id"));
    return Response.noContent().build();
  }

  @GET
  @Path("/{id}/organization")
  public WorkerOrganizationDto getOrganization(@PathParam("id") String id) {
    return service.findOrganization(RequestParams.id(id, "id"));
  }

  @PUT
  @Path("/{id}/organization")
  public WorkerOrganizationDto setOrganization(
      @PathParam("id") String id, @QueryParam("assignment") String assignment) {
    long workerId = RequestParams.id(id, "id");
    OrganizationAssignment value =
        validator.validate(
            JsonParamReader.read(
                assignment, "assignment", OrganizationAssignment.class, "expectedOrganizationId"));
    return service.assignOrganization(workerId, value);
  }

  @GET
  @Path("/salary/sum")
  public SalarySum salarySum() {
    return new SalarySum(service.salarySum());
  }

  @GET
  @Path("/position/greater-than/{position}")
  public CountResult countWithPositionGreaterThan(@PathParam("position") String position) {
    Position value = RequestParams.requiredEnum(position, "position", Position.class);
    return new CountResult(service.countWithPositionGreaterThan(value));
  }

  @GET
  @Path("/name/contains/{substring:.+}")
  public List<WorkerDto> findByNameContaining(@PathParam("substring") String substring) {
    return service.findByNameContaining(substring);
  }

  private WorkerInput workerInput(String raw) {
    return validator.validate(JsonParamReader.read(raw, "worker", WorkerInput.class));
  }
}
