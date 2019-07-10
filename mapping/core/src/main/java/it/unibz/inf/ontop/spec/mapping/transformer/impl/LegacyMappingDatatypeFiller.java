package it.unibz.inf.ontop.spec.mapping.transformer.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Inject;
import it.unibz.inf.ontop.datalog.*;
import it.unibz.inf.ontop.dbschema.Attribute;
import it.unibz.inf.ontop.dbschema.RelationDefinition;
import it.unibz.inf.ontop.exception.OntopInternalBugException;
import it.unibz.inf.ontop.exception.UnknownDatatypeException;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.injection.OntopMappingSettings;
import it.unibz.inf.ontop.injection.ProvenanceMappingFactory;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.UnaryIQTree;
import it.unibz.inf.ontop.iq.node.ConstructionNode;
import it.unibz.inf.ontop.iq.node.ExtensionalDataNode;
import it.unibz.inf.ontop.iq.transform.impl.LazyRecursiveIQTreeVisitingTransformer;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.atom.DistinctVariableOnlyDataAtom;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.*;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.TermType;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.model.type.impl.TermTypeInferenceTools;
import it.unibz.inf.ontop.spec.mapping.MappingWithProvenance;
import it.unibz.inf.ontop.spec.mapping.pp.PPMappingAssertionProvenance;
import it.unibz.inf.ontop.spec.mapping.transformer.MappingDatatypeFiller;
import it.unibz.inf.ontop.spec.mapping.utils.MappingTools;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Legacy code to infer datatypes not declared in the targets of mapping assertions.
 * Types are inferred from the DB metadata.
 */
public class LegacyMappingDatatypeFiller implements MappingDatatypeFiller {

    private static final Logger log = LoggerFactory.getLogger(LegacyMappingDatatypeFiller.class);

    private final OntopMappingSettings settings;
    private final TermFactory termFactory;
    private final TypeFactory typeFactory;
    private final TermTypeInferenceTools termTypeInferenceTools;
    private final QueryUnionSplitter unionSplitter;
    private final UnionFlattener unionNormalizer;
    private final IntermediateQueryFactory iqFactory;
    private final ProvenanceMappingFactory provMappingFactory;
    private final SubstitutionFactory substitutionFactory;
    private final AtomFactory atomFactory;

    @Inject
    private LegacyMappingDatatypeFiller(OntopMappingSettings settings,
                                        TermFactory termFactory,
                                        TypeFactory typeFactory,
                                        TermTypeInferenceTools termTypeInferenceTools,
                                        QueryUnionSplitter unionSplitter,
                                        UnionFlattener unionNormalizer,
                                        IntermediateQueryFactory iqFactory,
                                        ProvenanceMappingFactory provMappingFactory,
                                        SubstitutionFactory substitutionFactory,
                                        AtomFactory atomFactory) {
        this.settings = settings;
        this.termFactory = termFactory;
        this.typeFactory = typeFactory;
        this.termTypeInferenceTools = termTypeInferenceTools;
        this.unionSplitter = unionSplitter;
        this.unionNormalizer = unionNormalizer;
        this.iqFactory = iqFactory;
        this.provMappingFactory = provMappingFactory;
        this.substitutionFactory = substitutionFactory;
        this.atomFactory = atomFactory;
    }

    /***
     * Infers missing data types.
     * For each rule, gets the type from the rule head, and if absent, retrieves the type from the metadata.
     *
     * The behavior of type retrieval is the following for each rule:
     * . build a "termOccurrenceIndex", which is a map from variables to body atoms + position.
     * For ex, consider the rule: C(x, y) <- A(x, y) \wedge B(y)
     * The "termOccurrenceIndex" map is {
     *  x \mapsTo [<A(x, y), 1>],
     *  y \mapsTo [<A(x, y), 2>, <B(y), 1>]
     *  }
     *  . then take the first occurrence each variable (e.g. take <A(x, y), 2> for variable y),
     *  and assign to the variable the corresponding column type in the DB
     *  (e.g. for y, the type of column 1 of table A).
     *  . then inductively infer the types of functions (e.g. concat, ...) from the variable types.
     *  Only the outermost expression is assigned a type.
     *
     *  Assumptions:
     *  .rule body atoms are extensional
     *  .the corresponding column types are compatible (e.g the types for column 1 of A and column 1 of B)
     */

    @Override
    public MappingWithProvenance inferMissingDatatypes(MappingWithProvenance mapping) throws UnknownDatatypeException {

        try {
            ImmutableMap<IQ, PPMappingAssertionProvenance> iqMap = mapping.getProvenanceMap().entrySet().stream()
                    .filter(e -> !e.getKey().getTree().isDeclaredAsEmpty())
                    .flatMap(e -> (MappingTools.extractRDFPredicate(e.getKey()).isClass()
                            ? Stream.of(e.getKey())
                            : inferMissingDatatypes(e.getKey()))
                                .map(iq -> new AbstractMap.SimpleEntry<>(iq, e.getValue())))
                    .collect(ImmutableCollectors.toMap());

            return provMappingFactory.create(iqMap, mapping.getMetadata());
        }
        catch (UnknownDatatypeRuntimeException e) {
            throw new UnknownDatatypeException(e.getMessage());
        }
    }

    private Stream<IQ> inferMissingDatatypes(IQ iq0) {
        //case of data and object property
        return unionSplitter.splitUnion(unionNormalizer.optimize(iq0))
                .filter(iq -> !iq.getTree().isDeclaredAsEmpty())
                .map(iq -> {
                    DistinctVariableOnlyDataAtom pa = iq.getProjectionAtom();
                    ConstructionNode constructionNode = ((ConstructionNode)iq.getTree().getRootNode());
                    ImmutableSubstitution<ImmutableTerm> sub = constructionNode.getSubstitution();
                    ImmutableTerm object = sub.applyToVariable(pa.getTerm(2)); // third argument only
                    ImmutableTerm object2 = insertOperationDatatyping(
                            insertVariableDataTyping(object, createIndex(iq.getTree())));
                    Variable var2 = iq.getVariableGenerator().generateNewVariable();
                    DistinctVariableOnlyDataAtom pa2 = atomFactory.getDistinctVariableOnlyDataAtom(
                            pa.getPredicate(), pa.getTerm(0), pa.getTerm(1), var2);
                    ImmutableSubstitution<ImmutableTerm> sub2 = substitutionFactory.getSubstitution(
                            pa.getTerm(0), sub.applyToVariable(pa.getTerm(0)),
                            pa.getTerm(1), sub.applyToVariable(pa.getTerm(1)),
                            var2, object2);
                    return  iqFactory.createIQ(pa2, iqFactory.createUnaryIQTree(
                            iqFactory.createConstructionNode(pa2.getVariables(), sub2),
                            ((UnaryIQTree)iq.getTree()).getChild()));
                })
                .distinct();

    }


    private ImmutableMultimap<Variable, Attribute> createIndex(IQTree tree) {
        ImmutableMultimap.Builder<Variable, Attribute> termOccurenceIndex = ImmutableMultimap.builder();
        // ASSUMPTION: there are no CONSTRUCT nodes apart from the top level
        // TODO: a more light-weight version is needed (that does not change the tree)
        tree.acceptTransformer(new LazyRecursiveIQTreeVisitingTransformer(iqFactory) {
           @Override
           public IQTree transformExtensionalData(ExtensionalDataNode dataNode) {
               RelationDefinition td = dataNode.getProjectionAtom().getPredicate().getRelationDefinition();
               ImmutableList<? extends VariableOrGroundTerm> terms = dataNode.getProjectionAtom().getArguments();
               int i = 1; // position index
               for (VariableOrGroundTerm t : terms) {
                   if (t instanceof Variable) {
                       termOccurenceIndex.put((Variable) t, td.getAttribute(i));
                   }
                   i++; // increase the position index for the next variable
               }
               return dataNode;
           }
        });
        return termOccurenceIndex.build();
    }





    /**
     * This method wraps the variable that holds data property values with a data type predicate.
     * It will replace the variable with a new function symbol and update the rule atom.
     * However, if the users already defined the data-type in the mapping, this method simply accepts the function symbol.
     */
    private ImmutableTerm insertVariableDataTyping(ImmutableTerm term, ImmutableMultimap<Variable, Attribute> termOccurenceIndex) {

        if (term instanceof ImmutableFunctionalTerm) {
            ImmutableFunctionalTerm function = (ImmutableFunctionalTerm) term;
            Predicate functionSymbol = function.getFunctionSymbol();
            if (functionSymbol instanceof DatatypePredicate ||
                    (functionSymbol instanceof URITemplatePredicate)
                    || (functionSymbol instanceof BNodePredicate)) {
                // NO-OP for already assigned datatypes, or object properties, or bnodes
                return term;
            }
            else if (functionSymbol instanceof OperationPredicate) {
                ImmutableList<ImmutableTerm> terms = function.getTerms().stream()
                        .map(t -> insertVariableDataTyping(t, termOccurenceIndex))
                        .collect(ImmutableCollectors.toList());
                return termFactory.getImmutableFunctionalTerm((OperationPredicate)functionSymbol, terms);
            }
            throw new IllegalArgumentException("Unsupported subtype of: " + Function.class.getSimpleName());
        }
        else if (term instanceof Variable) {
            Variable variable = (Variable) term;
            Collection<Attribute> list = termOccurenceIndex.get(variable);
            if (list == null)
                throw new UnboundTargetVariableException(variable);

            Optional<RDFDatatype> ot = list.stream()
                    .filter(a -> a.getType() != 0)
                    // TODO: refactor this (unsafe)!!!
                    .map(a -> (RDFDatatype) a.getTermType())
                    .findFirst();
            Optional.empty();

            RDFDatatype type = getDatatype(ot, variable);
            ImmutableTerm newTerm = termFactory.getImmutableTypedTerm(variable, type);
            log.info("Datatype " + type + " for the value " + variable + " has been inferred from the database");
            return newTerm;
        }
        else if (term instanceof ValueConstant) {
            return termFactory.getImmutableTypedTerm(term, ((ValueConstant) term).getType());
        }

        throw new IllegalArgumentException("Unsupported subtype of: " + Term.class.getSimpleName());
    }

    /**
     * Following R2RML standard we do not infer the datatype for operation but we return the default value string
     */
    private ImmutableTerm insertOperationDatatyping(ImmutableTerm immutableTerm) {

        if (immutableTerm instanceof ImmutableFunctionalTerm) {
            ImmutableFunctionalTerm functionalTerm = (ImmutableFunctionalTerm) immutableTerm;
            if (functionalTerm.getFunctionSymbol() instanceof OperationPredicate) {
                Optional<TermType> inferredType = termTypeInferenceTools.inferType(functionalTerm);
                if (inferredType.isPresent()) {
                    return termFactory.getImmutableTypedTerm(
                            deleteExplicitTypes(functionalTerm),
                            // TODO: refactor the cast
                            (RDFDatatype) inferredType.get());
                }
                else {
                    return termFactory.getImmutableTypedTerm(
                            functionalTerm,
                            getDatatype(Optional.empty(), immutableTerm));
                }
            }
        }
        return immutableTerm;
    }

    /*
        remove all datatype annotations RECURSIVELY
     */
    private ImmutableTerm deleteExplicitTypes(ImmutableTerm term) {
        if (term instanceof ImmutableFunctionalTerm) {
            ImmutableFunctionalTerm function = (ImmutableFunctionalTerm) term;
            ImmutableList<ImmutableTerm> terms = function.getTerms().stream()
                    .map(t -> deleteExplicitTypes(t))
                    .collect(ImmutableCollectors.toList());

            if (function.getFunctionSymbol() instanceof DatatypePredicate)
                return terms.get(0);
            else
                return termFactory.getImmutableFunctionalTerm(function.getFunctionSymbol(), terms);
        }
        return term;
    }


    private RDFDatatype getDatatype(Optional<RDFDatatype> type, ImmutableTerm term) {
        if (settings.isDefaultDatatypeInferred())
            return type.orElseGet(typeFactory::getXsdStringDatatype);
        else {
            return type.orElseThrow(() -> new UnknownDatatypeRuntimeException("Impossible to determine the expected datatype for "+ term +"\n" +
                    "Possible solutions: \n" +
                    "- Add an explicit datatype in the mapping \n" +
                    "- Add in the .properties file the setting: ontop.inferDefaultDatatype = true\n" +
                    " and we will infer the default datatype (xsd:string)"));

        }
    }

    private static class UnboundTargetVariableException extends OntopInternalBugException {
        protected UnboundTargetVariableException(Variable variable) {
            super("Unknown variable in the head of a mapping:" + variable + ". Should have been detected earlier !");
        }
    }

    private static class UnknownDatatypeRuntimeException extends RuntimeException { // workaround for lambdas
        private UnknownDatatypeRuntimeException(String message) {
            super(message);
        }
    }


}
