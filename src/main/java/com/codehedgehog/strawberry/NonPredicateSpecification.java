package com.codehedgehog.strawberry;

import com.codehedgehog.strawberry.exceptions.BadRequestException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.Assert;

import javax.persistence.criteria.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Jon on 1/19/2019.
 */
public class NonPredicateSpecification<T> implements Specification<T> {
    private SearchCriteria nonPredicateCriterion;
    private List<SearchCriteria> predicateCriteria;

    public NonPredicateSpecification(SearchCriteria nonPredicateCriterion, List<SearchCriteria> predicateCriteria) {
        Assert.notNull(nonPredicateCriterion, "nonPredicateCriterion must not be null");
        Assert.notNull(predicateCriteria, "predicateCriteria must not be null");
        this.nonPredicateCriterion = nonPredicateCriterion;
        this.predicateCriteria = predicateCriteria;
    }

    @Override
    public javax.persistence.criteria.Predicate toPredicate(Root<T> root, CriteriaQuery<?> criteriaQuery,
                                                            CriteriaBuilder criteriaBuilder) {
        List<Map.Entry<SearchOperation, Object>> nonPredicateCriteria = nonPredicateCriterion.getOperationValueEntries();
        SearchOperation nonPredicateSearchOperation;
        Object nonPredicateValue;
        if (nonPredicateCriteria.size() > 1) {
            throw new BadRequestException("Multiple terminal queries for field " + nonPredicateCriterion.getKey());
        } else {
            nonPredicateSearchOperation = nonPredicateCriteria.get(0).getKey();
            nonPredicateValue = nonPredicateCriteria.get(0).getValue();
        }

        Class subqueryClass = root.get(nonPredicateValue.toString()).getClass();
        Class rootClass = root.getJavaType();

        Subquery<T> subquery = criteriaQuery.subquery(subqueryClass);
        Root<T> subqueryRoot = subquery.from(rootClass);

        switch(nonPredicateSearchOperation) {
            case GREATEST:
                Expression maxExpression = criteriaBuilder.greatest(
                        subqueryRoot.<String>get(nonPredicateValue.toString()));
                subquery.select(maxExpression);
                if (!predicateCriteria.isEmpty()) {
                    subquery.where(getSubquerySpecifications(subqueryRoot, criteriaQuery, criteriaBuilder));
                }
                break;
            case LEAST:
                Expression minExpression = criteriaBuilder.least(
                        subqueryRoot.<String>get(nonPredicateValue.toString()));
                subquery.select(minExpression);
                if (!predicateCriteria.isEmpty()) {
                    subquery.where(getSubquerySpecifications(subqueryRoot, criteriaQuery, criteriaBuilder));
                }
                break;
        }

        return criteriaBuilder.in(root.get(nonPredicateValue.toString())).value(subquery);
    }

    private Expression getSubquerySpecifications(Root<T> root, CriteriaQuery<?> criteriaQuery,
                                                 CriteriaBuilder criteriaBuilder) {
        Iterator<SearchCriteria> iterator = predicateCriteria.iterator();
        SearchCriteria searchCriteria = iterator.next();
        Predicate completePredicate = new GenericSpecification<T>(searchCriteria)
                .toPredicate(root, criteriaQuery, criteriaBuilder);
        while (iterator.hasNext()) {
            searchCriteria = iterator.next();
            completePredicate = criteriaBuilder.and(completePredicate,
                    new GenericSpecification<T>(searchCriteria).toPredicate(root, criteriaQuery, criteriaBuilder));
        }
        return completePredicate;
    }
}
