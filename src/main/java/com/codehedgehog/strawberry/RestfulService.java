package com.codehedgehog.strawberry;

import com.codehedgehog.strawberry.exceptions.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.EmbeddedId;
import javax.persistence.Id;
import javax.persistence.IdClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by Jon on 1/19/2019.
 */
public class RestfulService<T, ID extends Serializable> {
    private final Logger log = LoggerFactory.getLogger(RestfulService.class);
    public static final List<String> NON_FILTER_ACTIONS =
            Collections.unmodifiableList(Arrays.asList("sort", "include", "count", "start"));
    public static final List<String> NON_PREDICATE_TERMS =
            Collections.unmodifiableList(Arrays.asList("least", "greatest", "min", "max"));
    public static final List<SearchOperation> VALID_BOOLEAN_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL));
    public static final List<SearchOperation> VALID_STRING_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL, SearchOperation.ENDS, SearchOperation.STARTS,
                    SearchOperation.LIKE));
    public static final List<SearchOperation> VALID_NUMERIC_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL, SearchOperation.ENDS, SearchOperation.STARTS,
                    SearchOperation.LIKE, SearchOperation.GREATER_THAN, SearchOperation.LESS_THAN,
                    SearchOperation.GREATEST, SearchOperation.LEAST));
    public static final List<SearchOperation> VALID_DATE_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL, SearchOperation.GREATER_THAN,
                    SearchOperation.LESS_THAN, SearchOperation.GREATEST, SearchOperation.LEAST));
    public static final List<SearchOperation> VALID_CHARACTER_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL));


    private BaseJpaRepository<T, ID> baseJpaRepository;
    private Class<T>                 classType;

    public RestfulService(BaseJpaRepository<T, ID> baseJpaRepository) {
        this.baseJpaRepository = baseJpaRepository;
        this.classType = ((Class<T>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0]);
    }

    protected List<T> getObjects(Map<String, String[]> parameters) {
        List<T> objects;
        Field idField = this.getDefaultSortField();
        Sort sort = this.getSort(parameters, Sort.Direction.ASC, idField.getName());
        PageRequest pageRequest = this.getPageRequest(parameters, sort);
        List<SearchCriteria> searchCriteriaList = this.getSearchCriteria(parameters);
        if (searchCriteriaList.isEmpty()) {
            if (pageRequest != null) {
                Page pagedObjects = this.baseJpaRepository.findAll(pageRequest);
                objects = pagedObjects.getContent();
            } else {
                objects = this.baseJpaRepository.findAll(sort);
            }
        } else {
            Specification<T> objectSpecification = new SpecificationBuilder<T>().with(searchCriteriaList).build();
            if (pageRequest != null) {
                Page pagedObjects = this.baseJpaRepository.findAll(objectSpecification, pageRequest);
                objects = pagedObjects.getContent();
            } else {
                objects = this.baseJpaRepository.findAll(objectSpecification, sort);
            }
        }
        return objects;
    }

    protected T getObject(ID objectId) {
        return this.baseJpaRepository.findOne(objectId);
    }

    protected T saveObject(T object) {
        return this.baseJpaRepository.save(object);
    }

    protected void deleteObject(ID objectId) {
        this.baseJpaRepository.deleteById(objectId);
    }

    protected Sort getSort(Map<String, String[]> parameters, Sort.Direction defaultDirection, String defaultParameter) {
        List<Sort.Order> orders = new ArrayList<>();
        if (parameters.containsKey("sort")) {
            String[] values = parameters.get("sort");
            for (String value : values) {
                String sortParameter;
                Sort.Direction direction = Sort.Direction.ASC;
                sortParameter = value.trim();
                if (value.startsWith("-") || value.startsWith("+")) {
                    if (value.substring(0, 1).equals("-")) {
                        direction = Sort.Direction.DESC;
                    }
                    sortParameter = value.substring(1).trim();
                }
                if (!sortParameter.isEmpty()) {
                    orders.add(new Sort.Order(direction, sortParameter));
                    log.debug("Adding sort order for: {} with direction: {}",
                            sortParameter, direction.toString());
                }
            }
        }
        if (orders.isEmpty()) {
            orders.add(new Sort.Order(defaultDirection, defaultParameter));
            log.debug("Adding default sort order for: {} with direction: {}",
                    defaultParameter, defaultDirection.toString());
        }
        return new Sort(orders);
    }

    protected List<SearchCriteria> getSearchCriteria(Map<String, String[]> parameters) {
        Map<String, SearchCriteria> searchCriteriaMap = new HashMap<>();
        for (String key : parameters.keySet()) {
            log.debug("Parameter {} with values: {}", key, String.join(", ", parameters.get(key)));
            String[] separatedKey = key.split("\\.");
            String actionSpecifier = this.getRealKey(separatedKey[0]);
            boolean isNonPredicateKey = this.isNonPredicateKey(key);
            if (separatedKey.length <= 1 && !isNonPredicateKey) {
                // Enforce keys use reserved words or non-predicate keys only - ignore all others
                continue;
            }
            String fieldName = (!isNonPredicateKey ? separatedKey[1] : "");
            if (this.isNonFilterAction(actionSpecifier) || (!isNonPredicateKey && !this.isFieldOnObject(fieldName))) {
                // non-filter actions are handled elsewhere - skip them here
                // if the field does not exist on the entity, ignore it
                continue;
            }
            String specifiedOperation = "";
            if (separatedKey.length > 2) {
                specifiedOperation = separatedKey[2];
            }
            for (String value : parameters.get(key)) {
                if (isNonPredicateKey) {
                    specifiedOperation = actionSpecifier;
                    fieldName = actionSpecifier;
                }
                SearchOperation searchOperation = this.getSearchOperation(specifiedOperation, value);
                this.validateSearchOperationOnParameterType(searchOperation, getFieldOnObject(fieldName));
                if (searchCriteriaMap.containsKey(actionSpecifier)) {
                    searchCriteriaMap.get(actionSpecifier).addOperationValueEntry(searchOperation, value);
                } else {
                    searchCriteriaMap.put(actionSpecifier, new SearchCriteria(fieldName, searchOperation, value));
                }
            }
        }
        return new ArrayList<>(searchCriteriaMap.values());
    }

    protected PageRequest getPageRequest(Map<String, String[]> parameters, Sort sort) {
        Map<String, Integer> paginationParameters = this.getPaginationParameters(parameters);
        if (!paginationParameters.isEmpty()) {
            Integer start = 0;
            Integer count = 10;
            if (paginationParameters.containsKey("start")) {
                start = paginationParameters.get("start");
            }
            if (paginationParameters.containsKey("count")) {
                count = paginationParameters.get("count");
            }
            PageRequest pageRequest = new PageRequest(start, count, sort);
            return pageRequest;
        } else {
            return null;
        }
    }

    private boolean isFieldOnObject(String fieldName) {
        return Arrays.stream(this.classType.getDeclaredFields())
                .anyMatch(f -> f.getName().equals(fieldName));
    }

    private Field getFieldOnObject(String fieldName) {
        return Arrays.stream(this.classType.getDeclaredFields())
                .filter(f -> f.getName().equals(fieldName)).findFirst().orElse(null);
    }

    private Field getDefaultSortField() {
        return this.getEntityIdField().orElse(this.classType.getDeclaredFields()[0]);
    }

    private Optional<Field> getEntityIdField() {
        return Arrays.stream(this.classType.getDeclaredFields()).filter(f ->
                f.isAnnotationPresent(Id.class)
                        || f.isAnnotationPresent(EmbeddedId.class)
                        || f.isAnnotationPresent(IdClass.class)
        ).findAny();
    }

    private Map<String, Integer> getPaginationParameters(Map<String, String[]> parameters) {
        Map<String, Integer> paginationParameters = new HashMap<>();
        if (parameters.containsKey("count")) {
            paginationParameters.put("count", Integer.parseInt(parameters.get("count")[0]));
        }
        if (parameters.containsKey("start")) {
            paginationParameters.put("start", Integer.parseInt(parameters.get("start")[0]));
        }
        return paginationParameters;
    }

    private SearchOperation getSearchOperation(String dotParam, String value) {
        SearchOperation searchOperation;
        switch (dotParam.toLowerCase()) {
            case "before":
            case "less":
                searchOperation = SearchOperation.LESS_THAN;
                break;
            case "after":
            case "greater":
                searchOperation = SearchOperation.GREATER_THAN;
                break;
            case "like":
                searchOperation = SearchOperation.LIKE;
                break;
            case "starts":
                searchOperation = SearchOperation.STARTS;
                break;
            case "ends":
                searchOperation = SearchOperation.ENDS;
                break;
            case "not":
                searchOperation = SearchOperation.NOT_EQUAL;
                break;
            case "null":
                if (value.equalsIgnoreCase("true")) {
                    searchOperation = SearchOperation.NULL;
                } else if (value.equalsIgnoreCase("false")) {
                    searchOperation = SearchOperation.NOT_NULL;
                } else {
                    throw new BadRequestException("Invalid search criteria");
                }
                break;
            case "min":
            case "least":
                searchOperation = SearchOperation.LEAST;
                break;
            case "max":
            case "greatest":
                searchOperation = SearchOperation.GREATEST;
                break;
            case "":
                searchOperation = SearchOperation.EQUALS;
                break;
            default:
                throw new BadRequestException("Invalid search criteria");
        }
        return searchOperation;
    }

    private void validateSearchOperationOnParameterType(SearchOperation searchOperation, Field field) {
        boolean operationValid = true;
        if (field == null) {
            operationValid = false;
        } else {
            if (field.getType().isAssignableFrom(Number.class)) {
                if (!VALID_NUMERIC_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            }
            if (field.getType().isAssignableFrom(String.class)) {
                if (!VALID_STRING_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            }
            if (field.getType().isAssignableFrom(Date.class)
                    || field.getType().isAssignableFrom(Time.class)
                    || field.getType().isAssignableFrom(LocalDate.class)
                    || field.getType().isAssignableFrom(LocalDateTime.class)) {
                if (!VALID_DATE_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            }
            if (field.getType().isAssignableFrom(Boolean.class)) {
                if (!VALID_BOOLEAN_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            }
            if (field.getType().isAssignableFrom(Character.class)) {
                if (!VALID_CHARACTER_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            }
        }
        if (!operationValid) {
            throw new BadRequestException("Unable to perform operation of type " + searchOperation.toString()
                    + " on a field of type " + field.getType().toString());
        }
    }

    private Boolean isNonFilterAction(String key) {
        return NON_FILTER_ACTIONS.contains(key);
    }

    private Boolean isNonPredicateKey(String key) {
        return NON_PREDICATE_TERMS.contains(key);
    }

    private String getRealKey(String originalKey) {
        if (originalKey.equals("least")) {
            originalKey = "min";
        } else if (originalKey.equals("greatest")) {
            originalKey = "max";
        }
        return originalKey;
    }

}
