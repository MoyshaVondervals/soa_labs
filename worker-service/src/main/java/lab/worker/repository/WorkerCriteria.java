package lab.worker.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import lab.worker.model.Worker;
import lab.worker.query.FilterCondition;
import lab.worker.query.FilterOperator;
import lab.worker.query.SortOrder;
import lab.worker.query.WorkerField;

final class WorkerCriteria {
  private WorkerCriteria() {}

  static Predicate[] where(CriteriaBuilder cb, Root<Worker> root, List<FilterCondition> filters) {
    return filters.stream().map(f -> predicate(cb, root, f)).toArray(Predicate[]::new);
  }

  static List<Order> orderBy(CriteriaBuilder cb, Root<Worker> root, List<SortOrder> sorts) {
    List<Order> orders = new ArrayList<>();
    boolean byId = false;
    for (SortOrder sort : sorts) {
      Path<?> path = path(root, sort.field());
      orders.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(path), 1).otherwise(0)));
      Expression<?> key = sort.field().isEnum() ? rank(cb, path, sort.field()) : path;
      orders.add(sort.ascending() ? cb.asc(key) : cb.desc(key));
      byId |= sort.field() == WorkerField.ID;
    }
    if (!byId) orders.add(cb.asc(root.get("id")));
    return orders;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Predicate predicate(CriteriaBuilder cb, Root<Worker> root, FilterCondition f) {
    Path<?> path = path(root, f.field());
    Object value = f.value();
    if (value == null) return f.operator() == FilterOperator.EQ ? cb.isNull(path) : cb.isNotNull(path);
    Expression<Comparable> left;
    Comparable right;
    if (f.field().isEnum()) {
      left = (Expression) rank(cb, path, f.field());
      right = ((Enum<?>) value).ordinal();
    } else {
      left = (Expression<Comparable>) path;
      right = (Comparable) value;
    }
    return switch (f.operator()) {
      case EQ -> cb.equal(path, value);
      case NE -> cb.notEqual(path, value);
      case GT -> cb.greaterThan(left, right);
      case GTE -> cb.greaterThanOrEqualTo(left, right);
      case LT -> cb.lessThan(left, right);
      case LTE -> cb.lessThanOrEqualTo(left, right);
      case CONTAINS -> cb.greaterThan(cb.locate((Expression<String>) path, (String) value), 0);
    };
  }

  private static Path<?> path(Root<Worker> root, WorkerField field) {
    Path<?> path = root;
    for (String part : field.path().split("\\.")) path = path.get(part);
    return path;
  }

  private static Expression<Integer> rank(CriteriaBuilder cb, Path<?> path, WorkerField field) {
    CriteriaBuilder.Case<Integer> rank = cb.selectCase();
    for (Object constant : field.type().getEnumConstants())
      rank = rank.when(cb.equal(path, constant), ((Enum<?>) constant).ordinal());
    return rank.otherwise(cb.nullLiteral(Integer.class));
  }
}
