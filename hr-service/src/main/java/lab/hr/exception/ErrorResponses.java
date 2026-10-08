package lab.hr.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lab.hr.dto.ErrorResponse;
import lab.hr.dto.Violation;

final class ErrorResponses {
  private ErrorResponses() {}

  static Response of(int status, String message, List<Violation> violations, UriInfo uri) {
    List<Violation> details = null;
    if (status == 422)
      details = violations.isEmpty() ? List.of(new Violation("request", message)) : violations;
    ErrorResponse body =
        new ErrorResponse(
            OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS),
            status,
            reason(status),
            message,
            uri == null ? "/" : uri.getRequestUri().getPath(),
            details);
    return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
  }

  private static String reason(int status) {
    if (status == 422) return "Unprocessable Entity";
    Response.Status known = Response.Status.fromStatusCode(status);
    return known == null ? "Error" : known.getReasonPhrase();
  }
}
