package lab.hr.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import java.util.Set;
import lab.hr.exception.ApiException;

@Provider
@PreMatching
public class RequestBodyFilter implements ContainerRequestFilter {
  private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

  @Override
  public void filter(ContainerRequestContext request) {
    if (request.hasEntity() && METHODS_WITH_BODY.contains(request.getMethod()))
      throw new ApiException(
          415, "Data must be supplied through URL parameters; request bodies are unsupported");
  }
}
