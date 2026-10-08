package lab.hr.dto;

public record WorkerDto(
    long id,
    String name,
    Coordinates coordinates,
    String creationDate,
    double salary,
    String endDate,
    String position,
    String status,
    Person person) {
  public record Coordinates(long x, long y) {}

  public record Person(
      String passportID, String eyeColor, String hairColor, String nationality, Location location) {}

  public record Location(float x, double y, int z, String name) {}
}
