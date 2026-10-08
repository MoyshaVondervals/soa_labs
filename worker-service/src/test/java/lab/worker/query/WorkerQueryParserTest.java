package lab.worker.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.List;
import lab.worker.exception.ApiException;
import lab.worker.model.Position;
import org.junit.jupiter.api.Test;

class WorkerQueryParserTest {
  @Test
  void parsesFiltersOfDifferentTypes() {
    WorkerQuery query =
        WorkerQueryParser.parse(
            List.of(
                "salary:gte:50000",
                "position:gt:LABORER",
                "name:contains:Iv",
                "endDate:eq:null",
                "creationDate:lt:2030-01-01T00:00:00+03:00"),
            List.of());

    assertEquals(50000.0, query.filters().get(0).value());
    assertEquals(Position.LABORER, query.filters().get(1).value());
    assertEquals(FilterOperator.CONTAINS, query.filters().get(2).operator());
    assertNull(query.filters().get(3).value());
    assertEquals(
        OffsetDateTime.parse("2030-01-01T00:00:00+03:00"), query.filters().get(4).value());
  }

  @Test
  void parsesSortsInOrder() {
    WorkerQuery query = WorkerQueryParser.parse(List.of(), List.of("salary:desc", "name:asc"));

    assertEquals(
        List.of(new SortOrder(WorkerField.SALARY, false), new SortOrder(WorkerField.NAME, true)),
        query.sorts());
  }

  @Test
  void rejectsInvalidFiltersWith422() {
    for (String filter :
        List.of(
            "salary",
            "unknown:eq:1",
            "salary:like:1",
            "salary:eq:abc",
            "salary:gt:null",
            "salary:contains:1",
            "position:eq:CEO",
            "creationDate:eq:2026-01-01")) {
      ApiException e =
          assertThrows(
              ApiException.class, () -> WorkerQueryParser.parse(List.of(filter), List.of()), filter);
      assertEquals(422, e.getStatus(), filter);
    }
  }

  @Test
  void nullIsPlainTextForNonNullableStringField() {
    WorkerQuery query = WorkerQueryParser.parse(List.of("name:eq:null"), List.of());
    assertEquals("null", query.filters().get(0).value());
  }

  @Test
  void rejectsInvalidSortsWith422() {
    for (String sort : List.of("salary", "salary:up", "unknown:asc", "salary:asc:x")) {
      ApiException e =
          assertThrows(
              ApiException.class, () -> WorkerQueryParser.parse(List.of(), List.of(sort)), sort);
      assertEquals(422, e.getStatus(), sort);
    }
  }
}
