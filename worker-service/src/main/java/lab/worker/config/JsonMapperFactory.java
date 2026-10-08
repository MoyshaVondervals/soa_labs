package lab.worker.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;

public final class JsonMapperFactory {
  public static final ObjectMapper MAPPER = create();

  private JsonMapperFactory() {}

  private static ObjectMapper create() {
    JsonMapper mapper =
        JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(
                new SimpleModule()
                    .addDeserializer(OffsetDateTime.class, new Rfc3339DateTimeDeserializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    for (CoercionInputShape shape :
        new CoercionInputShape[] {
          CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean
        })
      mapper.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
    for (LogicalType type :
        new LogicalType[] {LogicalType.Integer, LogicalType.Float, LogicalType.Enum})
      mapper.coercionConfigFor(type).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
    return mapper;
  }
}
