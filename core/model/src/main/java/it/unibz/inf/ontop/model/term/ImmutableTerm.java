package it.unibz.inf.ontop.model.term;

import it.unibz.inf.ontop.model.type.TermTypeInference;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Term that is guaranteed to be immutable.
 *
 * In the future, every term should be immutable
 */
public interface ImmutableTerm {

    /**
     * Returns true if and only if the term is a NULL Constant.
     */
    boolean isNull();

    boolean isGround();

    Stream<Variable> getVariableStream();

    /**
     * Returns empty when no TermType has been inferred (missing information or fatal error)
     * and no non-fatal error has been detected.
     */
    Optional<TermTypeInference> inferType();

    EvaluationResult evaluateEq(ImmutableTerm otherTerm);

    ImmutableTerm simplify(boolean isInConstructionNodeInOptimizationPhase);
}
