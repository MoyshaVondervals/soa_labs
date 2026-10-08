package lab.worker.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;
import lab.worker.repository.OrganizationRepository;

@ApplicationScoped
public class OrganizationSeeder {
  private static final Map<Long, String> ORGANIZATIONS =
      Map.of(10L, "Организация 10", 20L, "Организация 20", 30L, "Организация 30");

  @Inject private OrganizationRepository organizations;

  @Transactional
  public void seed(@Observes @Initialized(ApplicationScoped.class) Object event) {
    ORGANIZATIONS.forEach(organizations::createIfAbsent);
  }
}
