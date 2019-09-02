package com.jonflacke.hedgehog;

import org.springframework.util.Assert;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Jon on 1/19/2019.
 */
public class SearchCriteria {
    private String  key;
    private List<Map.Entry<SearchOperation, Object>> operationValueEntries;

    public SearchCriteria(String key, SearchOperation operation, Object value) {
        Assert.notNull(key, "Key must not be null");
        Assert.notNull(operation, "Operation must not be null");
        Assert.notNull(value, "Value must not be null");
        this.key = key;
        this.operationValueEntries = new ArrayList<>();
        this.addOperationValueEntry(operation, value);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<Map.Entry<SearchOperation, Object>> getOperationValueEntries() {
        return operationValueEntries;
    }

    public void setOperationValueEntries(List<Map.Entry<SearchOperation, Object>> operationValueEntries) {
        this.operationValueEntries = operationValueEntries;
    }

    public void addOperationValueEntry(SearchOperation operation, Object value) {
        Map.Entry<SearchOperation, Object> operationValueEntry = new AbstractMap.SimpleEntry<>(operation, value);
        this.operationValueEntries.add(operationValueEntry);
    }
}
