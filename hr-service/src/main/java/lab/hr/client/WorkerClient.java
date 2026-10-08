package lab.hr.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import lab.hr.config.JsonMapperFactory;
import lab.hr.config.Settings;
import lab.hr.dto.ErrorResponse;
import lab.hr.dto.OrganizationAssignment;
import lab.hr.dto.WorkerOrganizationDto;
import lab.hr.exception.ApiException;

@ApplicationScoped
public class WorkerClient {
  private static final Set<Integer> FORWARDED_STATUSES = Set.of(400, 409, 410, 422);
  private static final String RETRY_HINT = "; check current organization before retrying";

  private Client client;

  @PostConstruct
  void init() {
    ClientBuilder builder =
        ClientBuilder.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS);
    String trustStore = Settings.get("worker.truststore", "");
    if (!trustStore.isEmpty()) builder.trustStore(loadTrustStore(trustStore));
    client = builder.build();
  }

  @PreDestroy
  void close() {
    client.close();
  }

  public WorkerOrganizationDto assignOrganization(long workerId, OrganizationAssignment assignment) {
    URI uri =
        URI.create(
            Settings.workerBaseUrl()
                + "/api/workers/"
                + workerId
                + "/organization?assignment="
                + URLEncoder.encode(toJson(assignment), StandardCharsets.UTF_8));
    try (Response response =
        client
            .target(uri)
            .request(MediaType.APPLICATION_JSON)

            .property("jersey.config.client.suppressHttpComplianceValidation", true)
            .method("PUT")) {
      return handle(response);
    } catch (ProcessingException e) {
      throw translate(e);
    }
  }

  private static WorkerOrganizationDto handle(Response response) {
    int status = response.getStatus();
    MediaType type = response.getMediaType();
    if (type == null || !MediaType.APPLICATION_JSON_TYPE.isCompatible(type))
      throw ApiException.badGateway("Worker API returned an unsupported response media type");
    String body = response.readEntity(String.class);
    if (status == 200) {
      try {
        return JsonMapperFactory.MAPPER.readValue(body, WorkerOrganizationDto.class);
      } catch (JsonProcessingException e) {
        throw ApiException.badGateway("Worker API returned an invalid response" + RETRY_HINT);
      }
    }
    if (FORWARDED_STATUSES.contains(status)) {
      ErrorResponse error;
      try {
        error = JsonMapperFactory.MAPPER.readValue(body, ErrorResponse.class);
      } catch (JsonProcessingException e) {
        throw ApiException.badGateway("Worker API returned an invalid error response");
      }
      throw new ApiException(status, error.message(), error.violations());
    }
    throw ApiException.badGateway("Worker API responded with unexpected status " + status);
  }

  private static ApiException translate(ProcessingException e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof SocketTimeoutException)
        return new ApiException(504, "Worker API request timed out" + RETRY_HINT);
      if (cause instanceof SSLException)
        return ApiException.badGateway("Worker API TLS validation failed");
      if (cause instanceof ConnectException || cause instanceof UnknownHostException) break;
    }
    return new ApiException(503, "Worker API is unavailable" + RETRY_HINT);
  }

  private static String toJson(Object value) {
    try {
      return JsonMapperFactory.MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static KeyStore loadTrustStore(String file) {
    try (InputStream in = Files.newInputStream(Path.of(file))) {
      String password = Settings.get("worker.truststore.password", "");
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(in, password.toCharArray());
      return store;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot load Worker API truststore " + file, e);
    }
  }
}
