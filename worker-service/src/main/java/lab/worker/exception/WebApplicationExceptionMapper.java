package lab.worker.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
  @Context private UriInfo uri;

  @Override
  public Response toResponse(WebApplicationException e) {
    int status = e.getResponse().getStatus();
    String message = e.getMessage() == null ? "Request failed" : e.getMessage();
    return ErrorResponses.of(status, message, List.of(), uri);
  }
}
