package lab.hr.config;

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

  public static String workerBaseUrl() {
    return withoutTrailingSlash(get("worker.base.url", "https://localhost:8543"));
  }

  public static String workerPublicBaseUrl() {
    return withoutTrailingSlash(get("worker.public.base.url", workerBaseUrl()));
  }

  private static String withoutTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
