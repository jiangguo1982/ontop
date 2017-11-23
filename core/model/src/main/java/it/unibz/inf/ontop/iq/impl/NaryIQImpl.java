package it.unibz.inf.ontop.iq.impl;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.NaryIQ;
import it.unibz.inf.ontop.iq.node.NaryOperatorNode;
import it.unibz.inf.ontop.model.term.ImmutableExpression;
import it.unibz.inf.ontop.model.term.VariableOrGroundTerm;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.utils.VariableGenerator;

import java.util.Optional;

public class NaryIQImpl extends AbstractCompositeIQ<NaryOperatorNode> implements NaryIQ {

    private final boolean isLifted;

    @AssistedInject
    private NaryIQImpl(@Assisted NaryOperatorNode rootNode, @Assisted ImmutableList<IQ> children,
                       @Assisted boolean isLifted) {
        super(rootNode, children);
        this.isLifted = isLifted;
        if (children.size() < 2)
            throw new IllegalArgumentException("At least two children are required for a n-ary node");
    }

    @AssistedInject
    private NaryIQImpl(@Assisted NaryOperatorNode rootNode, @Assisted ImmutableList<IQ> children) {
        this(rootNode, children, false);
    }

    @Override
    public IQ liftBinding(VariableGenerator variableGenerator) {
        return isLifted
                ? this
                : getRootNode().liftBinding(getChildren(), variableGenerator);
    }

    @Override
    public IQ applyDescendingSubstitution(ImmutableSubstitution<? extends VariableOrGroundTerm> descendingSubstitution,
                                          Optional<ImmutableExpression> constraint) {
        throw new RuntimeException("TODO: implement it");
    }
}
