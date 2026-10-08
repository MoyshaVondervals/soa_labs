package lab.hr.config;

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
    if (Settings.corsOrigins().contains(origin)) {
      var headers = response.getHeaders();
      headers.putSingle("Access-Control-Allow-Origin", origin);
      headers.putSingle("Access-Control-Allow-Methods", "POST, PATCH, OPTIONS");
      headers.putSingle("Access-Control-Allow-Headers", "Accept, Content-Type");
      headers.putSingle("Access-Control-Expose-Headers", "Location");
      headers.add("Vary", "Origin");
    }
    response.getHeaders().putSingle("Cache-Control", "no-store");
  }
}
