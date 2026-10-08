package lab.hr.exception;

import java.util.List;
import lab.hr.dto.Violation;

public class ApiException extends RuntimeException {
  private final int status;
  private final List<Violation> violations;

  public ApiException(int status, String message) {
    this(status, message, List.of());
  }

  public ApiException(int status, String message, List<Violation> violations) {
    super(message);
    this.status = status;
    this.violations = violations == null ? List.of() : List.copyOf(violations);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(400, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(409, message);
  }

  public static ApiException invalid(String field, String message) {
    return new ApiException(422, "Validation failed", List.of(new Violation(field, message)));
  }

  public static ApiException badGateway(String message) {
    return new ApiException(502, message);
  }

  public int getStatus() {
    return status;
  }

  public List<Violation> getViolations() {
    return violations;
  }
}
