package lab.worker.validation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import lab.worker.dto.Violation;
import lab.worker.exception.ApiException;

@ApplicationScoped
public class BeanValidator {
  @Inject private Validator validator;

  public BeanValidator() {}

  BeanValidator(Validator validator) {
    this.validator = validator;
  }

  public <T> T validate(T value) {
    List<Violation> violations =
        validator.validate(value).stream()
            .map(BeanValidator::toViolation)
            .sorted(Comparator.comparing(Violation::field))
            .toList();
    if (!violations.isEmpty()) throw ApiException.invalid(violations);
    return value;
  }

  private static Violation toViolation(ConstraintViolation<?> v) {
    return new Violation(v.getPropertyPath().toString(), v.getMessage());
  }
}
