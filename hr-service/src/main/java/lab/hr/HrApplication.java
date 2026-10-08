package lab.hr;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Map;
import java.util.Set;
import lab.hr.config.CorsFilter;
import lab.hr.config.JsonWriter;
import lab.hr.config.RequestBodyFilter;
import lab.hr.controller.HrController;
import lab.hr.exception.ApiExceptionMapper;
import lab.hr.exception.UnexpectedExceptionMapper;
import lab.hr.exception.WebApplicationExceptionMapper;

@ApplicationPath("/hr")
public class HrApplication extends Application {
  @Override
  public Set<Class<?>> getClasses() {
    return Set.of(
        HrController.class,
        JsonWriter.class,
        CorsFilter.class,
        RequestBodyFilter.class,
        ApiExceptionMapper.class,
        WebApplicationExceptionMapper.class,
        UnexpectedExceptionMapper.class);
  }

  @Override
  public Map<String, Object> getProperties() {
    return Map.of(
        "jersey.config.disableJsonBinding", true, "jersey.config.disableJsonProcessing", true);
  }
}
