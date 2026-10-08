package lab.worker;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import java.util.Set;
import lab.worker.config.CorsFilter;
import lab.worker.config.JsonWriter;
import lab.worker.config.RequestBodyFilter;
import lab.worker.controller.WorkerController;
import lab.worker.exception.ApiExceptionMapper;
import lab.worker.exception.UnexpectedExceptionMapper;
import lab.worker.exception.WebApplicationExceptionMapper;

@ApplicationPath("/api")
public class WorkerApplication extends Application {
  @Override
  public Set<Class<?>> getClasses() {
    return Set.of(
        WorkerController.class,
        JsonWriter.class,
        CorsFilter.class,
        RequestBodyFilter.class,
        ApiExceptionMapper.class,
        WebApplicationExceptionMapper.class,
        UnexpectedExceptionMapper.class);
  }
}
