package lab.worker.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import lab.worker.exception.ApiException;
import lab.worker.model.Status;
import org.junit.jupiter.api.Test;

class RequestParamsTest {
  @Test
  void idTypeErrorIs400AndRangeErrorIs422() {
    assertEquals(42, RequestParams.id("42", "id"));
    assertEquals(400, assertThrows(ApiException.class, () -> RequestParams.id("abc", "id")).getStatus());
    assertEquals(422, assertThrows(ApiException.class, () -> RequestParams.id("0", "id")).getStatus());
  }

  @Test
  void pageDefaultsAndBounds() {
    assertEquals(20, RequestParams.intInRange(null, "size", 20, 1, 1000));
    assertEquals(
        422,
        assertThrows(ApiException.class, () -> RequestParams.intInRange("1001", "size", 20, 1, 1000))
            .getStatus());
    assertEquals(
        400,
        assertThrows(ApiException.class, () -> RequestParams.intInRange("x", "size", 20, 1, 1000))
            .getStatus());
  }

  @Test
  void enumMissingIs400AndUnknownIs422() {
    assertEquals(Status.FIRED, RequestParams.requiredEnum("FIRED", "status", Status.class));
    assertEquals(
        400,
        assertThrows(ApiException.class, () -> RequestParams.requiredEnum(null, "status", Status.class))
            .getStatus());
    assertEquals(
        422,
        assertThrows(ApiException.class, () -> RequestParams.requiredEnum("X", "status", Status.class))
            .getStatus());
  }
}
