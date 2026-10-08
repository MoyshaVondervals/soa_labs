package lab.worker.query;

import java.util.List;

public record WorkerQuery(List<FilterCondition> filters, List<SortOrder> sorts) {}
