package org.semanticweb.ontop.pivotalrepr;

import java.util.Optional;

/**
 * TODO: explain
 */
public interface NodeTransformationProposal {

    NodeTransformationProposedState getState();

    Optional<QueryNode> getOptionalNewNode();
}
