package lab.hr.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lab.hr.dto.MoveResult;
import lab.hr.service.HrService;
import lab.hr.validation.RequestParams;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class HrController {
  @Inject private HrService service;

  @PATCH
  @Path("/fire/{id}")
  public Response fire(@PathParam("id") String id, @HeaderParam("Origin") String origin) {
    long workerId = RequestParams.id(id, "id");
    return Response.temporaryRedirect(service.fireLocation(workerId, origin)).build();
  }

  @POST
  @Path("/move/{worker-id}/{id-from}/{id-to}")
  public MoveResult move(
      @PathParam("worker-id") String workerId,
      @PathParam("id-from") String from,
      @PathParam("id-to") String to) {
    return service.move(
        RequestParams.id(workerId, "worker-id"),
        RequestParams.id(from, "id-from"),
        RequestParams.id(to, "id-to"));
  }
}
