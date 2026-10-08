package lab.worker.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lab.worker.exception.ApiException;

public final class WorkerQueryParser {
  private WorkerQueryParser() {}

  public static WorkerQuery parse(List<String> filters, List<String> sorts) {
    List<FilterCondition> conditions = new ArrayList<>();
    for (String filter : filters) conditions.add(parseFilter(filter));
    List<SortOrder> orders = new ArrayList<>();
    for (String sort : sorts) orders.add(parseSort(sort));
    return new WorkerQuery(List.copyOf(conditions), List.copyOf(orders));
  }

  private static FilterCondition parseFilter(String filter) {
    String[] parts = filter.split(":", 3);
    if (parts.length != 3) throw invalid("filter", "Expected field:operator:value");
    WorkerField field =
        WorkerField.byPath(parts[0])
            .orElseThrow(() -> invalid("filter", "Unknown field: " + parts[0]));
    FilterOperator operator =
        FilterOperator.byCode(parts[1])
            .orElseThrow(() -> invalid("filter", "Unknown operator: " + parts[1]));
    Object value;
    try {
      value = field.parse(parts[2]);
    } catch (IllegalArgumentException e) {
      throw invalid("filter", "Invalid value for " + field.path());
    }
    if (value == null && operator != FilterOperator.EQ && operator != FilterOperator.NE)
      throw invalid("filter", "null supports only eq/ne");
    if (operator == FilterOperator.CONTAINS && !field.isText())
      throw invalid("filter", "contains supports only string fields");
    return new FilterCondition(field, operator, value);
  }

  private static SortOrder parseSort(String sort) {
    String[] parts = sort.split(":", -1);
    Optional<WorkerField> field =
        parts.length == 2 ? WorkerField.byPath(parts[0]) : Optional.empty();
    if (field.isEmpty() || !(parts[1].equals("asc") || parts[1].equals("desc")))
      throw invalid("sort", "Expected allowedField:asc or allowedField:desc");
    return new SortOrder(field.get(), parts[1].equals("asc"));
  }

  private static ApiException invalid(String field, String message) {
    return ApiException.invalid(field, message);
  }
}
