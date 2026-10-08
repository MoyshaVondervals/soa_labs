package lab.worker.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext request) {
    if (request.getMethod().equals("OPTIONS")) request.abortWith(Response.noContent().build());
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    String origin = request.getHeaderString("Origin");
    if (Settings.corsOrigins().contains(origin) || isHrFireRedirect(request, origin)) {
      var headers = response.getHeaders();
      headers.putSingle("Access-Control-Allow-Origin", origin);
      headers.putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
      headers.putSingle("Access-Control-Allow-Headers", "Accept, Content-Type");
      headers.putSingle("Access-Control-Expose-Headers", "Location");
      headers.add("Vary", "Origin");
    }
    response.getHeaders().putSingle("Cache-Control", "no-store");
  }

  private static boolean isHrFireRedirect(ContainerRequestContext request, String origin) {
    String method = request.getMethod();
    boolean patch =
        method.equals("PATCH")
            || (method.equals("OPTIONS")
                && "PATCH".equals(request.getHeaderString("Access-Control-Request-Method")));
    return "null".equals(origin)
        && Settings.allowHrRedirect()
        && patch
        && request.getUriInfo().getPath().matches("/?workers/[0-9]+")
        && "FIRED".equals(request.getUriInfo().getQueryParameters().getFirst("status"));
  }
}
