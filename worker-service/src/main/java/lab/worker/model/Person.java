package lab.worker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class Person {
  @Column(name = "passport_id")
  private String passportID;

  @Enumerated(EnumType.STRING)
  @Column(name = "eye_color", nullable = false)
  private EyeColor eyeColor;

  @Enumerated(EnumType.STRING)
  @Column(name = "hair_color", nullable = false)
  private HairColor hairColor;

  @Enumerated(EnumType.STRING)
  @Column(name = "nationality")
  private Country nationality;

  @Embedded private Location location;

  public Person() {}

  public Person(
      String passportID,
      EyeColor eyeColor,
      HairColor hairColor,
      Country nationality,
      Location location) {
    this.passportID = passportID;
    this.eyeColor = eyeColor;
    this.hairColor = hairColor;
    this.nationality = nationality;
    this.location = location;
  }

  public String getPassportID() {
    return passportID;
  }

  public EyeColor getEyeColor() {
    return eyeColor;
  }

  public HairColor getHairColor() {
    return hairColor;
  }

  public Country getNationality() {
    return nationality;
  }

  public Location getLocation() {
    return location;
  }
}
