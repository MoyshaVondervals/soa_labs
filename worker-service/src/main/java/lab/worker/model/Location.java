package lab.worker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Location {
  @Column(name = "location_x")
  private Float x;

  @Column(name = "location_y")
  private Double y;

  @Column(name = "location_z")
  private Integer z;

  @Column(name = "location_name")
  private String name;

  public Location() {}

  public Location(Float x, Double y, Integer z, String name) {
    this.x = x;
    this.y = y;
    this.z = z;
    this.name = name;
  }

  public Float getX() {
    return x;
  }

  public Double getY() {
    return y;
  }

  public Integer getZ() {
    return z;
  }

  public String getName() {
    return name;
  }
}
