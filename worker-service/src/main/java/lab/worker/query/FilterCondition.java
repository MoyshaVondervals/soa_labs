package lab.worker.query;

public record FilterCondition(WorkerField field, FilterOperator operator, Object value) {}
