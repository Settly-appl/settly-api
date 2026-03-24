package pl.settly.settly_api.common.search;

import java.util.List;

public record PagedResponse<T>(List<T> result, Integer pageNumber, Integer numberOfPages) {}
