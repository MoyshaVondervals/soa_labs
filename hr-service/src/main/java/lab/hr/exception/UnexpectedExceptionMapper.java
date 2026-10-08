package lab.hr.exception;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {
  private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class.getName());

  @Context private UriInfo uri;

  @Override
  public Response toResponse(Throwable e) {
    LOG.log(Level.SEVERE, "Unhandled request error", e);
    return ErrorResponses.of(500, "Internal service error", List.of(), uri);
  }
}
