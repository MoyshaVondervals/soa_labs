package lab.worker.dto;

import java.util.List;

public record WorkerPage(
    List<WorkerDto> items, int page, int size, long totalItems, int totalPages) {}
