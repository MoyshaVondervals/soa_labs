package lab.worker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Coordinates {
  @Column(name = "coordinate_x", nullable = false)
  private Long x;

  @Column(name = "coordinate_y", nullable = false)
  private Long y;

  public Coordinates() {}

  public Coordinates(Long x, Long y) {
    this.x = x;
    this.y = y;
  }

  public Long getX() {
    return x;
  }

  public Long getY() {
    return y;
  }
}
