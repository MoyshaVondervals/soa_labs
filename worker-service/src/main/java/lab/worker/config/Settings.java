package lab.worker.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Settings {
  private Settings() {}

  public static String get(String key, String fallback) {
    String env = key.toUpperCase(Locale.ROOT).replace('.', '_');
    return System.getProperty(key, System.getenv().getOrDefault(env, fallback));
  }

  public static List<String> corsOrigins() {
    return Arrays.asList(get("app.cors.origin", "https://localhost:8543").split(","));
  }

  public static boolean allowHrRedirect() {
    return Boolean.parseBoolean(get("app.cors.allow.hr.redirect", "false"));
  }
}
