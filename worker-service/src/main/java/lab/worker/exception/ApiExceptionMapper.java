package lab.worker.exception;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
  @Context private UriInfo uri;

  @Override
  public Response toResponse(ApiException e) {
    return ErrorResponses.of(e.getStatus(), e.getMessage(), e.getViolations(), uri);
  }
}
