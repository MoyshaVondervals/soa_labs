package lab.worker.query;

import java.util.Arrays;
import java.util.Optional;

public enum FilterOperator {
  EQ("eq"),
  NE("ne"),
  GT("gt"),
  GTE("gte"),
  LT("lt"),
  LTE("lte"),
  CONTAINS("contains");

  private final String code;

  FilterOperator(String code) {
    this.code = code;
  }

  public static Optional<FilterOperator> byCode(String code) {
    return Arrays.stream(values()).filter(o -> o.code.equals(code)).findFirst();
  }
}
