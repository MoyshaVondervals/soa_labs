package lab.worker.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.validation.Validation;
import java.util.List;
import lab.worker.dto.OrganizationAssignment;
import lab.worker.dto.Violation;
import lab.worker.dto.WorkerInput;
import lab.worker.exception.ApiException;
import lab.worker.model.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class WorkerInputValidationTest {
  private static final String VALID =
      """
      {"name":"Ivan","coordinates":{"x":1,"y":714},"salary":100.5,"endDate":null,
       "position":"BAKER","status":"HIRED",
       "person":{"passportID":"1","eyeColor":"BROWN","hairColor":"RED","nationality":null,
                 "location":{"x":1.5,"y":2.5,"z":3,"name":null}}}
      """;

  private final BeanValidator validator =
      new BeanValidator(Validation.buildDefaultValidatorFactory().getValidator());

  private WorkerInput read(String json) {
    return validator.validate(JsonParamReader.read(json, "worker", WorkerInput.class));
  }

  private static ApiException fails(int status, Executable call) {
    ApiException e = assertThrows(ApiException.class, call);
    assertEquals(status, e.getStatus(), e.getMessage());
    return e;
  }

  @Test
  void acceptsValidWorker() {
    WorkerInput input = read(VALID);
    assertEquals(Position.BAKER, input.position());
    assertNull(input.endDate());
  }

  @Test
  void syntaxAndTypeErrorsAre400() {
    fails(400, () -> read(null));
    fails(400, () -> read("{"));
    fails(400, () -> read("[]"));
    fails(400, () -> read(VALID + "{}"));
    fails(400, () -> read(VALID.replace("\"name\":\"Ivan\"", "\"name\":\"Ivan\",\"name\":\"X\"")));
    fails(400, () -> read(VALID.replace("\"salary\":100.5", "\"salary\":\"100.5\"")));
    fails(400, () -> read(VALID.replace("\"x\":1,", "\"x\":1.5,")));
    fails(400, () -> read(VALID.replace("\"name\":\"Ivan\"", "\"name\":5")));
    fails(400, () -> read(VALID.replace("\"endDate\":null", "\"endDate\":\"2026-01-01\"")));
    fails(400, () -> read(VALID.replace("\"endDate\":null", "\"endDate\":1700000000")));
  }

  @Test
  void valueErrorsAre422WithFieldPaths() {
    ApiException e =
        fails(
            422,
            () ->
                read(
                    VALID
                        .replace("\"y\":714", "\"y\":715")
                        .replace("\"salary\":100.5", "\"salary\":0")
                        .replace("\"name\":\"Ivan\"", "\"name\":\"\"")));
    assertEquals(
        List.of(
            new Violation("coordinates.y", "must be less than or equal to 714"),
            new Violation("name", "must not be empty"),
            new Violation("salary", "must be greater than 0")),
        e.getViolations());

    assertEquals(
        "person.location.z",
        fails(422, () -> read(VALID.replace(",\"z\":3", ""))).getViolations().get(0).field());
    assertEquals(
        "position",
        fails(422, () -> read(VALID.replace("BAKER", "CEO"))).getViolations().get(0).field());
    assertEquals(
        "person.extra",
        fails(422, () -> read(VALID.replace("\"passportID\"", "\"extra\":1,\"passportID\"")))
            .getViolations()
            .get(0)
            .field());
  }

  @Test
  void endDateAcceptsRfc3339WithOffset() {
    WorkerInput input =
        read(VALID.replace("\"endDate\":null", "\"endDate\":\"2026-01-01T10:00:00+03:00\""));
    assertEquals("2026-01-01T10:00+03:00", input.endDate().toString());
  }

  @Test
  void assignmentRequiresExpectedKeyButAllowsNull() {
    OrganizationAssignment first =
        validator.validate(
            JsonParamReader.read(
                "{\"expectedOrganizationId\":null,\"organizationId\":10}",
                "assignment",
                OrganizationAssignment.class,
                "expectedOrganizationId"));
    assertNull(first.expectedOrganizationId());

    fails(
        422,
        () ->
            JsonParamReader.read(
                "{\"organizationId\":10}",
                "assignment",
                OrganizationAssignment.class,
                "expectedOrganizationId"));
    fails(
        422,
        () ->
            validator.validate(
                JsonParamReader.read(
                    "{\"expectedOrganizationId\":0,\"organizationId\":10}",
                    "assignment",
                    OrganizationAssignment.class)));
  }
}
