package com.jonflacke.hedgehog;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.Assert;

import java.util.*;

/**
 * Created by Jon on 1/19/2019.
 */
public class SpecificationBuilder<T> {
    public static final List<String> NON_PREDICATE_TERMS =
            Collections.unmodifiableList(Arrays.asList("least", "greatest", "min", "max"));
    private final List<SearchCriteria> searchCriteriaList;

    public SpecificationBuilder() { this.searchCriteriaList = new ArrayList<>(); }

    public SpecificationBuilder with(List<SearchCriteria> searchCriteriaList) {
        this.searchCriteriaList.addAll(searchCriteriaList);
        return this;
    }

    public Specification<T> build() {
        Assert.notEmpty(searchCriteriaList, "Search Parameters must not be empty");
        Collections.sort(searchCriteriaList, Comparator.comparing(SearchCriteria::getKey));
        if (containsNonPredicateTerms()) {
            return getNonPredicateSpecification();
        } else {
            return getPredicateOnlySpecification();
        }
    }

    private Specification<T> getPredicateOnlySpecification() {
        Iterator<SearchCriteria> iterator = searchCriteriaList.iterator();
        SearchCriteria searchCriteria = iterator.next();
        Specification<T> spec = Specification.where(new GenericSpecification<T>(searchCriteria));
        while (iterator.hasNext()) {
            searchCriteria = iterator.next();
            spec = spec.and(new GenericSpecification<T>(searchCriteria));
        }
        return spec;
    }

    private Specification<T> getNonPredicateSpecification() {
        List<SearchCriteria> nonPredicateCriteria = new ArrayList<>();
        List<SearchCriteria> predicateCriteria = new ArrayList<>();
        separatePredicatesAndNonPredicates(nonPredicateCriteria, predicateCriteria);

        Iterator<SearchCriteria> iterator = nonPredicateCriteria.iterator();
        SearchCriteria nonPredicateSearchCriterion = iterator.next();
        Specification<T> spec = Specification.where(
                new NonPredicateSpecification<T>(nonPredicateSearchCriterion, predicateCriteria));
        while(iterator.hasNext()) {
            nonPredicateSearchCriterion = iterator.next();
            spec = spec.and(new NonPredicateSpecification<T>(nonPredicateSearchCriterion, predicateCriteria));
        }

        spec = getPredicateSpecification(predicateCriteria, spec);

        return spec;
    }

    private void separatePredicatesAndNonPredicates(List<SearchCriteria> nonPredicateCriteria,
                                       List<SearchCriteria> predicateCriteria) {
        for (SearchCriteria searchCriterion : searchCriteriaList) {
            if (isNonPredicateTerm(searchCriterion)) {
                nonPredicateCriteria.add(searchCriterion);
            } else {
                predicateCriteria.add(searchCriterion);
            }
        }
    }

    private Specification<T> getPredicateSpecification(List<SearchCriteria> predicateCriteria, Specification<T> spec) {
        Iterator<SearchCriteria> iterator = predicateCriteria.iterator();
        while (iterator.hasNext()) {
            SearchCriteria searchCriteria = iterator.next();
            spec = spec.and(new GenericSpecification<T>(searchCriteria));
        }
        return spec;
    }

    private Boolean containsNonPredicateTerms() {
        for (SearchCriteria searchCriterion : searchCriteriaList) {
            if (isNonPredicateTerm(searchCriterion)) {
                return true;
            }
        }
        return false;
    }

    private Boolean isNonPredicateTerm(SearchCriteria searchCriterion) {
        for (String term : NON_PREDICATE_TERMS) {
            if (term.equalsIgnoreCase(searchCriterion.getKey())) {
                return true;
            }
        }
        return false;
    }
}
