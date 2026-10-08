package lab.hr.validation;

import lab.hr.exception.ApiException;

public final class RequestParams {
  private RequestParams() {}

  public static long id(String text, String field) {
    if (text == null) throw ApiException.badRequest("Missing parameter: " + field);
    long value;
    try {
      value = Long.parseLong(text);
    } catch (NumberFormatException e) {
      throw ApiException.badRequest(field + " must be an int64 integer");
    }
    if (value < 1) throw ApiException.invalid(field, "must be greater than 0");
    return value;
  }
}
