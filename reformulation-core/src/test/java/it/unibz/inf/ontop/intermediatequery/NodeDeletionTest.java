package it.unibz.inf.ontop.intermediatequery;

import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.model.*;
import it.unibz.inf.ontop.model.impl.AtomPredicateImpl;
import it.unibz.inf.ontop.model.impl.OBDADataFactoryImpl;
import org.junit.Test;
import it.unibz.inf.ontop.owlrefplatform.core.optimization.BasicJoinOptimizer;
import it.unibz.inf.ontop.pivotalrepr.EmptyQueryException;
import it.unibz.inf.ontop.owlrefplatform.core.optimization.IntermediateQueryOptimizer;
import it.unibz.inf.ontop.pivotalrepr.impl.*;
import it.unibz.inf.ontop.pivotalrepr.impl.tree.DefaultIntermediateQueryBuilder;
import it.unibz.inf.ontop.pivotalrepr.*;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO: test
 */
public class NodeDeletionTest {

    private static final OBDADataFactory DATA_FACTORY = OBDADataFactoryImpl.getInstance();
    private static final MetadataForQueryOptimization METADATA = new EmptyMetadataForQueryOptimization();


    @Test(expected = EmptyQueryException.class)
    public void testSimpleJoin() throws IntermediateQueryBuilderException, EmptyQueryException {
        Variable x = DATA_FACTORY.getVariable("x");
        ConstructionNode rootNode = new ConstructionNodeImpl(ImmutableSet.of(x));
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(new AtomPredicateImpl("ans1", 1), x);

        IntermediateQueryBuilder queryBuilder = new DefaultIntermediateQueryBuilder(METADATA);
        queryBuilder.init(projectionAtom, rootNode);

        ValueConstant falseValue = DATA_FACTORY.getBooleanConstant(false);
        ImmutableExpression falseCondition = DATA_FACTORY.getImmutableExpression(ExpressionOperation.AND, falseValue, falseValue);

        InnerJoinNode joinNode = new InnerJoinNodeImpl(Optional.of(falseCondition));
        queryBuilder.addChild(rootNode, joinNode);

        ExtensionalDataNode table1 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table1", 1), x));
        queryBuilder.addChild(joinNode, table1);

        ExtensionalDataNode table2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table2", 1), x));
        queryBuilder.addChild(joinNode, table2);

        IntermediateQuery initialQuery = queryBuilder.build();
        System.out.println("Initial query: " + initialQuery.toString());

        IntermediateQueryOptimizer joinOptimizer = new BasicJoinOptimizer();

        /**
         * Should throw the EmptyQueryException
         */
        IntermediateQuery optimizedQuery = joinOptimizer.optimize(initialQuery);
        System.err.println("Optimized query (should have been rejected): " + optimizedQuery.toString());
    }

    @Test
    public void testInvalidRightPartOfLeftJoin1() throws IntermediateQueryBuilderException, EmptyQueryException {
        Variable x = DATA_FACTORY.getVariable("x");
        Variable y = DATA_FACTORY.getVariable("y");

        ConstructionNode rootNode = new ConstructionNodeImpl(ImmutableSet.of(x,y));
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(
                new AtomPredicateImpl("ans1", 2), x, y);

        IntermediateQueryBuilder queryBuilder = new DefaultIntermediateQueryBuilder(METADATA);
        queryBuilder.init(projectionAtom, rootNode);

        ValueConstant falseValue = DATA_FACTORY.getBooleanConstant(false);
        ImmutableExpression falseCondition = DATA_FACTORY.getImmutableExpression(ExpressionOperation.AND, falseValue, falseValue);

        LeftJoinNode ljNode = new LeftJoinNodeImpl(Optional.<ImmutableExpression>empty());
        queryBuilder.addChild(rootNode, ljNode);

        String table1Name = "table1";
        ExtensionalDataNode table1 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl(table1Name, 1), x));
        queryBuilder.addChild(ljNode, table1, NonCommutativeOperatorNode.ArgumentPosition.LEFT);

        InnerJoinNode joinNode = new InnerJoinNodeImpl(Optional.of(falseCondition));
        queryBuilder.addChild(ljNode, joinNode, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);

        ExtensionalDataNode table2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table2", 2), x, y));
        queryBuilder.addChild(joinNode, table2);

        ExtensionalDataNode table3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table3", 2), x, y));
        queryBuilder.addChild(joinNode, table3);

        IntermediateQuery initialQuery = queryBuilder.build();
        System.out.println("Initial query: " + initialQuery.toString());

        IntermediateQueryOptimizer joinOptimizer = new BasicJoinOptimizer();

        /**
         * Should replace the left join node by table 1.
         */
        IntermediateQuery optimizedQuery = joinOptimizer.optimize(initialQuery);
        System.out.println("Optimized query : " + optimizedQuery.toString());

        QueryNode viceRootNode = optimizedQuery.getFirstChild(optimizedQuery.getRootConstructionNode()).get();
        assertTrue(viceRootNode instanceof ExtensionalDataNode);
        assertEquals(((ExtensionalDataNode) viceRootNode).getProjectionAtom().getPredicate().getName(), table1Name);
        assertTrue(optimizedQuery.getChildren(viceRootNode).isEmpty());
    }

    @Test
    public void testUnion1() throws IntermediateQueryBuilderException, EmptyQueryException {
        Variable x = DATA_FACTORY.getVariable("x");
        Variable y = DATA_FACTORY.getVariable("y");

        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(
                new AtomPredicateImpl("ans1", 2), x, y);
        ImmutableSet<Variable> projectedVariables = projectionAtom.getVariables();

        ConstructionNode rootNode = new ConstructionNodeImpl(projectedVariables);

        IntermediateQueryBuilder queryBuilder = new DefaultIntermediateQueryBuilder(METADATA);
        queryBuilder.init(projectionAtom, rootNode);

        ValueConstant falseValue = DATA_FACTORY.getBooleanConstant(false);
        ImmutableExpression falseCondition = DATA_FACTORY.getImmutableExpression(ExpressionOperation.AND, falseValue, falseValue);

        UnionNode topUnion = new UnionNodeImpl(projectedVariables);
        queryBuilder.addChild(rootNode, topUnion);

        //DistinctVariableOnlyDataAtom subAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(new AtomPredicateImpl("ansu1", 2), x, y);
        ConstructionNode constructionNode1 = new ConstructionNodeImpl(projectedVariables);
        queryBuilder.addChild(topUnion, constructionNode1);

        String table1Name = "table1";
        ExtensionalDataNode table1 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl(table1Name, 2), x, y));
        queryBuilder.addChild(constructionNode1, table1);

        ConstructionNode constructionNode2 = new ConstructionNodeImpl(projectedVariables);
        queryBuilder.addChild(topUnion, constructionNode2);

        InnerJoinNode joinNode1 = new InnerJoinNodeImpl(Optional.of(falseCondition));
        queryBuilder.addChild(constructionNode2, joinNode1);

        ExtensionalDataNode table2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table2", 2), x, y));
        queryBuilder.addChild(joinNode1, table2);

        ExtensionalDataNode table3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table3", 2), x, y));
        queryBuilder.addChild(joinNode1, table3);

        ConstructionNode constructionNode3 = new ConstructionNodeImpl(projectedVariables);
        queryBuilder.addChild(topUnion, constructionNode3);

        InnerJoinNode joinNode2 = new InnerJoinNodeImpl(Optional.of(falseCondition));
        queryBuilder.addChild(constructionNode3, joinNode2);

        ExtensionalDataNode table4 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table4", 2), x, y));
        queryBuilder.addChild(joinNode2, table4);

        ExtensionalDataNode table5 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table5", 2), x, y));
        queryBuilder.addChild(joinNode2, table5);

        IntermediateQuery initialQuery = queryBuilder.build();
        System.out.println("Initial query: " + initialQuery.toString());

        IntermediateQueryOptimizer joinOptimizer = new BasicJoinOptimizer();

        /**
         * Should replace the left join node by table 1.
         */
        IntermediateQuery optimizedQuery = joinOptimizer.optimize(initialQuery);
        System.out.println("Optimized query : " + optimizedQuery.toString());

        QueryNode viceRootNode = optimizedQuery.getFirstChild(optimizedQuery.getRootConstructionNode()).get();
        assertTrue(viceRootNode instanceof ConstructionNode);
        assertEquals(optimizedQuery.getChildren(viceRootNode).size(), 1);

        QueryNode viceViceRootNode = optimizedQuery.getFirstChild(viceRootNode).get();
        assertTrue(viceViceRootNode instanceof ExtensionalDataNode);
        assertEquals(((ExtensionalDataNode) viceViceRootNode).getProjectionAtom().getPredicate().getName(), table1Name);
        assertTrue(optimizedQuery.getChildren(viceViceRootNode).isEmpty());
    }

    @Test
    public void testUnion2() throws IntermediateQueryBuilderException, EmptyQueryException {
        Variable x = DATA_FACTORY.getVariable("x");
        Variable y = DATA_FACTORY.getVariable("y");

        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(
                new AtomPredicateImpl("ans1", 2), x, y);
        ImmutableSet<Variable> projectedVariables = projectionAtom.getVariables();
        ConstructionNode rootNode = new ConstructionNodeImpl(projectedVariables);


        IntermediateQueryBuilder queryBuilder = new DefaultIntermediateQueryBuilder(METADATA);
        queryBuilder.init(projectionAtom, rootNode);

        ValueConstant falseValue = DATA_FACTORY.getBooleanConstant(false);
        ImmutableExpression falseCondition = DATA_FACTORY.getImmutableExpression(ExpressionOperation.AND, falseValue, falseValue);

        UnionNode topUnion = new UnionNodeImpl(projectedVariables);
        queryBuilder.addChild(rootNode, topUnion);

        //DataAtom subAtom = DATA_FACTORY.getDataAtom(new AtomPredicateImpl("ansu1", 2), x, y);
        
        ConstructionNode constructionNode1 = new ConstructionNodeImpl(projectedVariables);
        queryBuilder.addChild(topUnion, constructionNode1);

        String table1Name = "table1";
        ExtensionalDataNode table1 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl(table1Name, 2), x, y));
        queryBuilder.addChild(constructionNode1, table1);

        ConstructionNode constructionNode2 = new ConstructionNodeImpl(projectedVariables);
        queryBuilder.addChild(topUnion, constructionNode2);

        InnerJoinNode joinNode1 = new InnerJoinNodeImpl(Optional.of(falseCondition));
        queryBuilder.addChild(constructionNode2, joinNode1);

        ExtensionalDataNode table2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table2", 2), x, y));
        queryBuilder.addChild(joinNode1, table2);

        ExtensionalDataNode table3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table3", 2), x, y));
        queryBuilder.addChild(joinNode1, table3);

        ConstructionNode constructionNode3 = new ConstructionNodeImpl(projectedVariables);
        queryBuilder.addChild(topUnion, constructionNode3);

        InnerJoinNode joinNode2 = new InnerJoinNodeImpl(Optional.<ImmutableExpression>empty());
        queryBuilder.addChild(constructionNode3, joinNode2);

        ExtensionalDataNode table4 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table4", 2), x, y));
        queryBuilder.addChild(joinNode2, table4);

        ExtensionalDataNode table5 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table5", 2), x, y));
        queryBuilder.addChild(joinNode2, table5);

        IntermediateQuery initialQuery = queryBuilder.build();
        System.out.println("Initial query: " + initialQuery.toString());

        IntermediateQueryOptimizer joinOptimizer = new BasicJoinOptimizer();

        /**
         * Should replace the left join node by table 1.
         */
        IntermediateQuery optimizedQuery = joinOptimizer.optimize(initialQuery);
        System.out.println("Optimized query : " + optimizedQuery.toString());

        QueryNode viceRootNode = optimizedQuery.getFirstChild(optimizedQuery.getRootConstructionNode()).get();
        assertTrue(viceRootNode instanceof UnionNode);
        assertEquals(optimizedQuery.getChildren(viceRootNode).size(), 2);
    }

    @Test(expected = EmptyQueryException.class)
    public void testInvalidLeftPartOfLeftJoin() throws IntermediateQueryBuilderException, EmptyQueryException {
        Variable x = DATA_FACTORY.getVariable("x");
        Variable y = DATA_FACTORY.getVariable("y");

        ConstructionNode rootNode = new ConstructionNodeImpl(ImmutableSet.of(x,y));
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(
                new AtomPredicateImpl("ans1", 2), x, y);

        IntermediateQueryBuilder queryBuilder = new DefaultIntermediateQueryBuilder(METADATA);
        queryBuilder.init(projectionAtom, rootNode);

        ValueConstant falseValue = DATA_FACTORY.getBooleanConstant(false);
        ImmutableExpression falseCondition = DATA_FACTORY.getImmutableExpression(ExpressionOperation.AND, falseValue, falseValue);

        LeftJoinNode ljNode = new LeftJoinNodeImpl(Optional.<ImmutableExpression>empty());
        queryBuilder.addChild(rootNode, ljNode);

        InnerJoinNode joinNode = new InnerJoinNodeImpl(Optional.of(falseCondition));
        queryBuilder.addChild(ljNode, joinNode, NonCommutativeOperatorNode.ArgumentPosition.LEFT);

        ExtensionalDataNode table2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table2", 2), x, y));
        queryBuilder.addChild(joinNode, table2);

        ExtensionalDataNode table3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table3", 2), x, y));
        queryBuilder.addChild(joinNode, table3);

        ExtensionalDataNode table4 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(new AtomPredicateImpl("table4", 1), x));
        queryBuilder.addChild(ljNode, table4, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);


        IntermediateQuery initialQuery = queryBuilder.build();
        System.out.println("Initial query: " + initialQuery.toString());

        IntermediateQueryOptimizer joinOptimizer = new BasicJoinOptimizer();

        /**
         * Should throw the EmptyQueryException
         */
        IntermediateQuery optimizedQuery = joinOptimizer.optimize(initialQuery);
        System.err.println("Optimized query (should have been rejected): " + optimizedQuery.toString());
    }
}