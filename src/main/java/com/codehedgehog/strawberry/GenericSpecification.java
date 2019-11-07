package com.codehedgehog.strawberry;

import com.codehedgehog.strawberry.exceptions.BadRequestException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.*;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by Jon on 1/19/2019.
 */
public class GenericSpecification<T> implements Specification<T> {
    private static final String SQL_LIKE = "%";
    private SearchCriteria searchCriteria;

    public GenericSpecification(SearchCriteria searchCriteria) {
        this.searchCriteria = searchCriteria;
    }

    /**
     * Creates a specification predicate for each search term based on the class' search criteria object
     * @param root entity on which to search
     * @param query the criteria query on which to build
     * @param criteriaBuilder the builder for the criteria query
     * @return predicate to use in specification search
     */
    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        Comparator<Map.Entry<SearchOperation, Object>> searchOperationMapComparator = (entry1, entry2) -> {
            return entry1.getKey().compareTo(entry2.getKey());
        };

        List<Map.Entry<SearchOperation, Object>> operations = searchCriteria.getOperationValueEntries();
        Collections.sort(operations, searchOperationMapComparator);
        Iterator<Map.Entry<SearchOperation, Object>> iterator = operations.iterator();
        List<String> seenParams = new ArrayList<>();
        Map.Entry<SearchOperation, Object> entry = iterator.next();
        Predicate returnPredicate = this.getSearchPredicate(root, criteriaBuilder, entry);

        addSeenParam(seenParams, entry);

        while (iterator.hasNext()) {
            entry = iterator.next();
            if (seenParams.contains(searchCriteria.getKey() + entry.getKey().toString())
                    || (seenParams.contains(searchCriteria.getKey()) && entry.getKey().equals(SearchOperation.NULL))) {
                returnPredicate = criteriaBuilder.or(returnPredicate, this.getSearchPredicate(root, criteriaBuilder, entry));
            } else {
                returnPredicate = criteriaBuilder.and(returnPredicate, this.getSearchPredicate(root, criteriaBuilder, entry));
                addSeenParam(seenParams, entry);
            }
        }
        return returnPredicate;
    }

    /**
     * Keeps track of all seen parameters, including the specific operations
     * performed (i.e. like, equals, greater, etc)
     * @param seenParams current list of seen parameters
     * @param entry the entry to add to the list
     */
    private void addSeenParam(List<String> seenParams, Map.Entry<SearchOperation, Object> entry) {
        seenParams.add(searchCriteria.getKey() + entry.getKey().toString());
        seenParams.add(searchCriteria.getKey());
    }

    /**
     * Gets the individual search predicate based on the key and operation
     * @param root root entity on which to search
     * @param criteriaBuilder builder for the criteria query
     * @param operationValueEntry operation and value with which to build
     * @return predicate for operation and value
     */
    private Predicate getSearchPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Map.Entry<SearchOperation,
            Object> operationValueEntry) {
        try {
            final String value = operationValueEntry.getValue().toString().toLowerCase();
            Class<?> javaType = getEntityExpressionObject(root).getJavaType();
            switch (operationValueEntry.getKey()) {
                case LIKE:
                    return criteriaBuilder.like(criteriaBuilder.lower(getEntityExpressionString(root)),
                            SQL_LIKE + this.getCastValue(javaType, value) + SQL_LIKE);
                case STARTS:
                    return criteriaBuilder.like(criteriaBuilder.lower(getEntityExpressionString(root)),
                            this.getCastValue(javaType, value) + SQL_LIKE);
                case ENDS:
                    return criteriaBuilder.like(criteriaBuilder.lower(getEntityExpressionString(root)),
                            SQL_LIKE + this.getCastValue(javaType, value));
                case EQUALS:
                    return criteriaBuilder.equal(criteriaBuilder.lower(getEntityExpressionString(root)),
                            this.getCastValue(javaType, value));
                case NOT_EQUAL:
                    return criteriaBuilder.notEqual(criteriaBuilder.lower(getEntityExpressionString(root)),
                            this.getCastValue(javaType, value));
                case LESS_THAN:
                    return criteriaBuilder.lessThan(getEntityExpressionString(root),
                            (Comparable) this.getCastValue(javaType, value));
                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(getEntityExpressionString(root),
                            (Comparable) this.getCastValue(javaType, value));
                case NULL:
                    return criteriaBuilder.isNull(getEntityExpressionObject(root));
                case NOT_NULL:
                    return criteriaBuilder.isNotNull(getEntityExpressionObject(root));
                default:
                    return null;
            }
        } catch (ParseException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Gets the string of the path to the entity field for operation
     * @param root entity root
     * @return string path to entity field
     */
    private Path<String> getEntityExpressionString(Root<T> root) {
        String[] paths = searchCriteria.getKey().split("\\.");
        Path<String> partialPath = root.get(paths[0]);
        for (int i = 1; i < paths.length; i++) {
            partialPath = partialPath.get(paths[i]);
        }
        return partialPath;
    }

    /**
     * Gets the path object to the entity field for operation
     * @param root entity root
     * @return object path to entity field
     */
    private Path<Object> getEntityExpressionObject(Root<T> root) {
        String[] paths = searchCriteria.getKey().split("\\.");
        Path<Object> partialPath = root.get(paths[0]);
        for (int i = 1; i < paths.length; i++) {
            partialPath = partialPath.get(paths[i]);
        }
        return partialPath;
    }

    /**
     * Casts the value from the string supplied via JSON into the class type of the entity field
     * @param classType the entity field type into which to attempt to cast
     * @param value the value which to cast
     * @return the value casted into the appropriate object
     * @throws ParseException if value is not assignable from class type
     */
    private Object getCastValue(Class classType, String value) throws ParseException {
        if (Boolean.class.isAssignableFrom(classType)) {
            return Boolean.valueOf(value);
        } else if (LocalDate.class.isAssignableFrom(classType)) {
            return LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } else if (Time.class.isAssignableFrom(classType)) {
            return Time.valueOf(value);
        } else if (Date.class.isAssignableFrom(classType)) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.parse(value);
        } else if (Enum.class.isAssignableFrom(classType)){
            return getEnumFromString(classType, value);
        } else {
            return value;
        }
    }

    /**
     * Converts enums from the string value to enum value
     * @param <T> Enum type
     * @param classType enum class type. All enums MUST be all caps.
     * @param value case insensitive
     * @return corresponding enum, or null
     */
    private static <T extends Enum<T>> T getEnumFromString(Class<T> classType, String value) throws IllegalArgumentException {
        if( classType != null && !StringUtils.isEmpty(value) ) {
            return Enum.valueOf(classType, value.trim().toUpperCase());
        }
        return null;
    }
}
