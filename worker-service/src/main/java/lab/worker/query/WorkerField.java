package lab.worker.query;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import lab.worker.config.Rfc3339DateTimeDeserializer;
import lab.worker.model.Country;
import lab.worker.model.EyeColor;
import lab.worker.model.HairColor;
import lab.worker.model.Position;
import lab.worker.model.Status;

public enum WorkerField {
  ID("id", Long.class, false),
  NAME("name", String.class, false),
  COORDINATES_X("coordinates.x", Long.class, false),
  COORDINATES_Y("coordinates.y", Long.class, false),
  CREATION_DATE("creationDate", OffsetDateTime.class, false),
  SALARY("salary", Double.class, false),
  END_DATE("endDate", OffsetDateTime.class, true),
  POSITION("position", Position.class, false),
  STATUS("status", Status.class, false),
  PERSON_PASSPORT_ID("person.passportID", String.class, true),
  PERSON_EYE_COLOR("person.eyeColor", EyeColor.class, false),
  PERSON_HAIR_COLOR("person.hairColor", HairColor.class, false),
  PERSON_NATIONALITY("person.nationality", Country.class, true),
  PERSON_LOCATION_X("person.location.x", Float.class, true),
  PERSON_LOCATION_Y("person.location.y", Double.class, true),
  PERSON_LOCATION_Z("person.location.z", Integer.class, true),
  PERSON_LOCATION_NAME("person.location.name", String.class, true);

  private final String path;
  private final Class<?> type;
  private final boolean nullable;

  WorkerField(String path, Class<?> type, boolean nullable) {
    this.path = path;
    this.type = type;
    this.nullable = nullable;
  }

  public static Optional<WorkerField> byPath(String path) {
    return Arrays.stream(values()).filter(f -> f.path.equals(path)).findFirst();
  }

  public String path() {
    return path;
  }

  public Class<?> type() {
    return type;
  }

  public boolean isText() {
    return type == String.class;
  }

  public boolean isEnum() {
    return type.isEnum();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Object parse(String raw) {
    if (nullable && raw.equals("null")) return null;
    if (type == String.class) return raw;
    if (type == Long.class) return Long.valueOf(raw);
    if (type == Integer.class) return Integer.valueOf(raw);
    if (type == Double.class) return finite(Double.parseDouble(raw));
    if (type == Float.class) return (float) finite(Float.parseFloat(raw));
    if (type == OffsetDateTime.class) {
      try {
        return Rfc3339DateTimeDeserializer.parse(raw);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException(e);
      }
    }
    return Enum.valueOf((Class<? extends Enum>) type, raw);
  }

  private static double finite(double value) {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("not finite");
    return value;
  }
}
