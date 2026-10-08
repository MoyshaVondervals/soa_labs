package lab.worker.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lab.worker.model.Position;
import lab.worker.model.Worker;
import lab.worker.query.WorkerQuery;

@ApplicationScoped
public class WorkerRepository {
  @PersistenceContext private EntityManager em;

  public Optional<Worker> findById(long id) {
    return Optional.ofNullable(em.find(Worker.class, id));
  }

  public Optional<Worker> findByIdForUpdate(long id) {
    return Optional.ofNullable(em.find(Worker.class, id, LockModeType.PESSIMISTIC_WRITE));
  }

  public Worker save(Worker worker) {
    em.persist(worker);
    return worker;
  }

  public void delete(Worker worker) {
    em.remove(worker);
  }

  public List<Worker> findPage(WorkerQuery query, int offset, int limit) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Worker> cq = cb.createQuery(Worker.class);
    Root<Worker> root = cq.from(Worker.class);
    cq.select(root)
        .where(WorkerCriteria.where(cb, root, query.filters()))
        .orderBy(WorkerCriteria.orderBy(cb, root, query.sorts()));
    return em.createQuery(cq).setFirstResult(offset).setMaxResults(limit).getResultList();
  }

  public long count(WorkerQuery query) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Long> cq = cb.createQuery(Long.class);
    Root<Worker> root = cq.from(Worker.class);
    cq.select(cb.count(root)).where(WorkerCriteria.where(cb, root, query.filters()));
    return em.createQuery(cq).getSingleResult();
  }

  public double sumSalary() {
    Double sum =
        em.createQuery("select sum(w.salary) from Worker w", Double.class).getSingleResult();
    return sum == null ? 0 : sum;
  }

  public long countByPositionIn(Collection<Position> positions) {
    if (positions.isEmpty()) return 0;
    return em.createQuery(
            "select count(w) from Worker w where w.position in :positions", Long.class)
        .setParameter("positions", positions)
        .getSingleResult();
  }

  public List<Worker> findByNameContaining(String substring) {
    return em.createQuery(
            "select w from Worker w where locate(:substring, w.name) > 0 order by w.id",
            Worker.class)
        .setParameter("substring", substring)
        .getResultList();
  }
}
