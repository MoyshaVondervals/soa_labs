package lab.worker.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.Arrays;
import java.util.stream.Collectors;
import lab.worker.config.JsonMapperFactory;
import lab.worker.exception.ApiException;

public final class JsonParamReader {
  private JsonParamReader() {}

  public static <T> T read(String raw, String param, Class<T> type, String... requiredKeys) {
    if (raw == null) throw ApiException.badRequest("Missing query parameter: " + param);
    try {
      JsonNode tree = JsonMapperFactory.MAPPER.readTree(raw);
      if (tree == null || !tree.isObject())
        throw ApiException.badRequest("Query parameter " + param + " must be a JSON object");
      for (String key : requiredKeys)
        if (!tree.has(key)) throw ApiException.invalid(key, "is required (null is allowed)");
      return JsonMapperFactory.MAPPER.treeToValue(tree, type);
    } catch (UnrecognizedPropertyException e) {
      throw ApiException.invalid(path(e), "unknown field");
    } catch (InvalidFormatException e) {
      if (e.getTargetType() != null && e.getTargetType().isEnum())
        throw ApiException.invalid(
            path(e), "must be one of " + Arrays.toString(e.getTargetType().getEnumConstants()));
      throw ApiException.badRequest(path(e) + " has invalid format");
    } catch (MismatchedInputException e) {
      throw ApiException.badRequest(path(e) + " has invalid type");
    } catch (JsonMappingException e) {
      throw ApiException.badRequest(path(e) + " has invalid value");
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("Query parameter " + param + " contains malformed JSON");
    }
  }

  private static String path(JsonMappingException e) {
    String path =
        e.getPath().stream()
            .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
            .collect(Collectors.joining("."));
    return path.isEmpty() ? "request" : path;
  }
}
