package lab.worker.config;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JsonWriter implements MessageBodyWriter<Object> {
  @Override
  public boolean isWriteable(
      Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType) && type != String.class;
  }

  @Override
  public void writeTo(
      Object value,
      Class<?> type,
      Type genericType,
      Annotation[] annotations,
      MediaType mediaType,
      MultivaluedMap<String, Object> headers,
      OutputStream out)
      throws IOException {
    JsonMapperFactory.MAPPER.writeValue(out, value);
  }
}
