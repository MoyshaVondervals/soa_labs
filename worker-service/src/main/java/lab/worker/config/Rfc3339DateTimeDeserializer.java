package lab.worker.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

public class Rfc3339DateTimeDeserializer extends StdScalarDeserializer<OffsetDateTime> {
  public static final Pattern FORMAT =
      Pattern.compile(
          "\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})");

  public Rfc3339DateTimeDeserializer() {
    super(OffsetDateTime.class);
  }

  public static OffsetDateTime parse(String text) {
    if (!FORMAT.matcher(text).matches()) throw new DateTimeException(text);
    return OffsetDateTime.parse(text.toUpperCase());
  }

  @Override
  public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    if (p.currentToken() != JsonToken.VALUE_STRING)
      return (OffsetDateTime) ctxt.handleUnexpectedToken(OffsetDateTime.class, p);
    try {
      return parse(p.getText());
    } catch (DateTimeException e) {
      return (OffsetDateTime)
          ctxt.handleWeirdStringValue(
              OffsetDateTime.class, p.getText(), "expected RFC 3339 date-time with offset");
    }
  }
}
