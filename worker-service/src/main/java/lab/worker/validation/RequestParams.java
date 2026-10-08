package lab.worker.validation;

import java.util.Arrays;
import lab.worker.exception.ApiException;

public final class RequestParams {
  private RequestParams() {}

  public static long id(String text, String field) {
    long value = parseLong(text, field);
    if (value < 1) throw ApiException.invalid(field, "must be greater than 0");
    return value;
  }

  public static int intInRange(String text, String field, int defaultValue, int min, int max) {
    if (text == null) return defaultValue;
    int value;
    try {
      value = Integer.parseInt(text);
    } catch (NumberFormatException e) {
      throw ApiException.badRequest(field + " must be an int32 integer");
    }
    if (value < min || value > max)
      throw ApiException.invalid(field, "must be between " + min + " and " + max);
    return value;
  }

  public static <E extends Enum<E>> E requiredEnum(String text, String field, Class<E> type) {
    if (text == null) throw ApiException.badRequest("Missing query parameter: " + field);
    return optionalEnum(text, field, type);
  }

  public static <E extends Enum<E>> E optionalEnum(String text, String field, Class<E> type) {
    if (text == null) return null;
    try {
      return Enum.valueOf(type, text);
    } catch (IllegalArgumentException e) {
      throw ApiException.invalid(
          field, "must be one of " + Arrays.toString(type.getEnumConstants()));
    }
  }

  private static long parseLong(String text, String field) {
    if (text == null) throw ApiException.badRequest("Missing parameter: " + field);
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException e) {
      throw ApiException.badRequest(field + " must be an int64 integer");
    }
  }
}
