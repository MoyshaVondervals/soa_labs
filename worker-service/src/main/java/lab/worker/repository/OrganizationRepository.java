package lab.worker.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import lab.worker.model.Organization;

@ApplicationScoped
public class OrganizationRepository {
  @PersistenceContext private EntityManager em;

  public Optional<Organization> findById(long id) {
    return Optional.ofNullable(em.find(Organization.class, id));
  }

  public void createIfAbsent(long id, String name) {
    if (em.find(Organization.class, id) == null) em.persist(new Organization(id, name));
  }
}
