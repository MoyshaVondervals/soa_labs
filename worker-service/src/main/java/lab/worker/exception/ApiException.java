package lab.worker.exception;

import java.util.List;
import lab.worker.dto.Violation;

public class ApiException extends RuntimeException {
  private final int status;
  private final List<Violation> violations;

  public ApiException(int status, String message) {
    this(status, message, List.of());
  }

  public ApiException(int status, String message, List<Violation> violations) {
    super(message);
    this.status = status;
    this.violations = List.copyOf(violations);
  }

  public static ApiException badRequest(String message) {
    return new ApiException(400, message);
  }

  public static ApiException conflict(String message) {
    return new ApiException(409, message);
  }

  public static ApiException gone(String message) {
    return new ApiException(410, message);
  }

  public static ApiException invalid(String field, String message) {
    return invalid(List.of(new Violation(field, message)));
  }

  public static ApiException invalid(List<Violation> violations) {
    return new ApiException(422, "Validation failed", violations);
  }

  public int getStatus() {
    return status;
  }

  public List<Violation> getViolations() {
    return violations;
  }
}
