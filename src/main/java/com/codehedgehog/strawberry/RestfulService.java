package com.codehedgehog.strawberry;

import com.codehedgehog.strawberry.exceptions.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import javax.persistence.EmbeddedId;
import javax.persistence.Id;
import javax.persistence.IdClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by Jon on 1/19/2019.
 */
public class RestfulService<R extends BaseJpaRepository<T, ID>, T, ID extends Serializable> {

    private static final List<String> NON_FILTER_ACTIONS =
            Collections.unmodifiableList(Arrays.asList("sort", "include", "count", "start"));
    private static final List<String> FILTER_ACTIONS =
            Collections.unmodifiableList(Arrays.asList(
                    "equals", "before", "less", "after", "greater", "like", "starts"," ends", "not", "null"));
    private static final List<String> NON_PREDICATE_TERMS =
            Collections.unmodifiableList(Arrays.asList("least", "greatest", "min", "max"));

    private static final List<SearchOperation> VALID_BOOLEAN_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL));
    private static final List<SearchOperation> VALID_STRING_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL, SearchOperation.ENDS, SearchOperation.STARTS,
                    SearchOperation.LIKE));
    private static final List<SearchOperation> VALID_ENUM_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL, SearchOperation.ENDS, SearchOperation.STARTS,
                    SearchOperation.LIKE));
    private static final List<SearchOperation> VALID_NUMERIC_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL, SearchOperation.ENDS, SearchOperation.STARTS,
                    SearchOperation.LIKE, SearchOperation.GREATER_THAN, SearchOperation.LESS_THAN,
                    SearchOperation.GREATEST, SearchOperation.LEAST));
    private static final List<SearchOperation> VALID_DATE_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL, SearchOperation.GREATER_THAN,
                    SearchOperation.LESS_THAN, SearchOperation.GREATEST, SearchOperation.LEAST));
    private static final List<SearchOperation> VALID_CHARACTER_OPERATORS =
            Collections.unmodifiableList(Arrays.asList(SearchOperation.EQUALS, SearchOperation.NOT_EQUAL,
                    SearchOperation.NULL, SearchOperation.NOT_NULL));

    private final Logger log = LoggerFactory.getLogger(RestfulService.class);

    protected R baseJpaRepository;

    private   Class<T>                 classType;

    public RestfulService(R baseJpaRepository) {
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
        return Sort.by(orders);
    }

    protected List<SearchCriteria> getSearchCriteria(Map<String, String[]> parameters) {
        Map<String, SearchCriteria> searchCriteriaMap = new HashMap<>();
        for (String key : parameters.keySet()) {
            log.debug("Parameter {} with values: {}", key, String.join(", ", parameters.get(key)));
            String[] separatedKey = key.split("\\.");

            if (separatedKey.length <= 1) {
                // Enforce keys use reserved words or non-predicate keys only - ignore all others
                continue;
            }
            String actionSpecifier = this.getActionSpecifier(separatedKey[1]);
            boolean isNonPredicateKey = this.isNonPredicateKey(actionSpecifier);

            String fieldName = "";
            String specifiedOperation = "";
            if (!isNonPredicateKey) {
                Map<String, String> fieldAndOperation = this.decipherAndValidateFieldNameAndSpecifiedOperation(separatedKey);
                fieldName = fieldAndOperation.get("fieldName");
                specifiedOperation = fieldAndOperation.get("specifiedOperation");
            }
            if (this.isNonFilterAction(actionSpecifier) || (!isNonPredicateKey && StringUtils.isEmpty(fieldName))) {
                // non-filter actions are handled elsewhere - skip them here
                // if the field is empty & key a predicate key, field does not exist on the entity so ignore it
                continue;
            }

            this.createOperationForEachParameterValue(parameters, searchCriteriaMap, key, actionSpecifier, isNonPredicateKey, fieldName, specifiedOperation);
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

    private void createOperationForEachParameterValue(Map<String, String[]> parameters, Map<String,
            SearchCriteria> searchCriteriaMap, String key, String actionSpecifier, boolean isNonPredicateKey,
                                                      String fieldName, String specifiedOperation) {
        for (String value : parameters.get(key)) {
            if (isNonPredicateKey) {
                SearchOperation searchOperation = this.getSearchOperation(actionSpecifier, value);
                this.validateSearchOperationOnParameterType(searchOperation, EntityTraversalUtility.getDeepestFieldOnObject(this.classType, value));
                if (searchCriteriaMap.containsKey(searchOperation.name())) {
                    searchCriteriaMap.get(searchOperation.name()).addOperationValueEntry(searchOperation, value);
                } else {
                    searchCriteriaMap.put(searchOperation.name(), new SearchCriteria(actionSpecifier, searchOperation, value));
                }
            } else {
                SearchOperation searchOperation = this.getSearchOperation(specifiedOperation, value);
                this.validateSearchOperationOnParameterType(searchOperation, EntityTraversalUtility.getDeepestFieldOnObject(this.classType, fieldName));
                if (searchCriteriaMap.containsKey(searchOperation.name())) {
                    searchCriteriaMap.get(searchOperation.name()).addOperationValueEntry(searchOperation, value);
                } else {
                    searchCriteriaMap.put(searchOperation.name(), new SearchCriteria(fieldName, searchOperation, value));
                }
            }
        }
    }

    private Map<String, String> decipherAndValidateFieldNameAndSpecifiedOperation(String[] separatedKey) {
        String fieldName = "";
        String specifiedOperation = "";
        if (separatedKey.length == 2) {
            if (EntityTraversalUtility.isFieldOnObject(this.classType, separatedKey[1])) {
                fieldName = separatedKey[1];
            }
        } else {
            String fullSuppliedPath = EntityTraversalUtility.rejoinFieldsFromSecondIndex(separatedKey);
            if (EntityTraversalUtility.isFieldOnObject(this.classType, fullSuppliedPath)) {
                fieldName = fullSuppliedPath;
            } else if (FILTER_ACTIONS.contains(separatedKey[separatedKey.length-1])) {
                String suppliedPathWithoutOperator = EntityTraversalUtility.rejoinFieldsWithoutLastIndex(separatedKey);
                if (EntityTraversalUtility.isFieldOnObject(this.classType, suppliedPathWithoutOperator)) {
                    // field is valid and last index is operator
                    fieldName = suppliedPathWithoutOperator;
                    specifiedOperation = separatedKey[separatedKey.length-1];
                }
            }
        }
        return createFieldAndOperationMap(fieldName, specifiedOperation);
    }

    private Map<String, String> createFieldAndOperationMap(String fieldName, String specifiedOperation) {
        Map<String, String> fieldAndOperation = new HashMap<>();
        fieldAndOperation.put("fieldName", fieldName);
        fieldAndOperation.put("specifiedOperation", specifiedOperation);
        return fieldAndOperation;
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
            case "equal":
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
            } else if (field.getType().isAssignableFrom(String.class)) {
                if (!VALID_STRING_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            } else if (field.getType().isAssignableFrom(Date.class)
                    || field.getType().isAssignableFrom(Time.class)
                    || field.getType().isAssignableFrom(LocalDate.class)
                    || field.getType().isAssignableFrom(LocalDateTime.class)) {
                if (!VALID_DATE_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            } else if (field.getType().isAssignableFrom(Boolean.class)) {
                if (!VALID_BOOLEAN_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            } else if (field.getType().isAssignableFrom(Character.class)) {
                if (!VALID_CHARACTER_OPERATORS.contains(searchOperation)) {
                    operationValid = false;
                }
            } else if (field.getType().isAssignableFrom(Enum.class)) {
                if (!VALID_ENUM_OPERATORS.contains(searchOperation)) {
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

    private String getActionSpecifier(String originalKey) {
        if (originalKey.equals("least")) {
            originalKey = "min";
        } else if (originalKey.equals("greatest")) {
            originalKey = "max";
        }
        return originalKey;
    }

}
