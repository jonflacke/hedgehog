package com.codehedgehog.strawberry;

import java.util.Arrays;
import java.util.Optional;

/**
 * Created by Jon on 1/19/2019.
 */
public enum SearchOperation {
    LIKE("like"), EQUALS("equals"), LESS_THAN("less"), GREATER_THAN("greater"), NOT_EQUAL("not equal"),
    GREATEST("greatest"), LEAST("least"), NULL("null"), NOT_NULL("not null"), STARTS("starts"), ENDS("ends");

    private final String operation;

    SearchOperation(String operation) { this.operation = operation; }

    public static SearchOperation fromString(String operation)
    {
        Optional<SearchOperation> first = Arrays.stream(SearchOperation.values())
                .filter(searchOperation -> operation.equalsIgnoreCase(String.valueOf(searchOperation.operation)))
                .findFirst();
        return first.orElse(null );
    }
}
