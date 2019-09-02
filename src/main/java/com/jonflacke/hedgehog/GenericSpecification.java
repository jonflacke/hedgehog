package com.jonflacke.hedgehog;

import com.jonflacke.hedgehog.exceptions.BadRequestException;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
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

    private void addSeenParam(List<String> seenParams, Map.Entry<SearchOperation, Object> entry) {
        seenParams.add(searchCriteria.getKey() + entry.getKey().toString());
        seenParams.add(searchCriteria.getKey());
    }

    private Predicate getSearchPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Map.Entry<SearchOperation,
            Object> operationValueEntry) {
        try {
            switch (operationValueEntry.getKey()) {
                case LIKE:
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get(searchCriteria.getKey())),
                            SQL_LIKE + this.getCastValue(root.get(searchCriteria.getKey()).getJavaType(),
                                    operationValueEntry.getValue().toString().toLowerCase()) + SQL_LIKE);
                case STARTS:
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get(searchCriteria.getKey())),
                            this.getCastValue(root.get(searchCriteria.getKey()).getJavaType(),
                                    operationValueEntry.getValue().toString().toLowerCase()) + SQL_LIKE);
                case ENDS:
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get(searchCriteria.getKey())),
                            SQL_LIKE + this.getCastValue(root.get(searchCriteria.getKey()).getJavaType(),
                                    operationValueEntry.getValue().toString().toLowerCase()));
                case EQUALS:
                    return criteriaBuilder.equal(root.get(searchCriteria.getKey()),
                            this.getCastValue(root.get(searchCriteria.getKey()).getJavaType(),
                                    operationValueEntry.getValue().toString()));
                case NOT_EQUAL:
                    return criteriaBuilder.notEqual(root.get(searchCriteria.getKey()),
                            this.getCastValue(root.get(searchCriteria.getKey()).getJavaType(),
                                    operationValueEntry.getValue().toString()));
                case LESS_THAN:
                    return criteriaBuilder.lessThan(root.get(searchCriteria.getKey()),
                            (Comparable) this.getCastValue(
                                    root.get(searchCriteria.getKey()).getJavaType(),
                                    operationValueEntry.getValue().toString()));
                case GREATER_THAN:
                    return criteriaBuilder.greaterThan(root.get(searchCriteria.getKey()),
                            (Comparable) this.getCastValue(
                                    root.get(searchCriteria.getKey()).getJavaType(),
                                    operationValueEntry.getValue().toString()));
                case NULL:
                    return criteriaBuilder.isNull(root.get(searchCriteria.getKey()));
                case NOT_NULL:
                    return criteriaBuilder.isNotNull(root.get(searchCriteria.getKey()));
                default:
                    return null;
            }
        } catch (ParseException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private Object getCastValue(Class classType, String value) throws ParseException {
        if (Boolean.class.isAssignableFrom(classType)) {
            return Boolean.valueOf(value);
        } else if (LocalDate.class.isAssignableFrom(classType)) {
            return LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmss"));
        } else if (Time.class.isAssignableFrom(classType)) {
            return Time.valueOf(value);
        } else if (Date.class.isAssignableFrom(classType)) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.parse(value);
        } else {
            return value;
        }
    }
}
