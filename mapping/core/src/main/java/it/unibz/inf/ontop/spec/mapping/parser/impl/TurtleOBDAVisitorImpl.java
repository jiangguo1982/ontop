package it.unibz.inf.ontop.spec.mapping.parser.impl;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.ExpressionOperation;
import it.unibz.inf.ontop.model.term.functionsymbol.Predicate;
import it.unibz.inf.ontop.model.term.functionsymbol.URITemplatePredicate;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.model.vocabulary.XSD;
import it.unibz.inf.ontop.spec.mapping.parser.impl.TurtleOBDAParser.*;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.simple.SimpleRDF;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static it.unibz.inf.ontop.model.IriConstants.RDF_TYPE;

public class TurtleOBDAVisitorImpl extends TurtleOBDABaseVisitor implements TurtleOBDAVisitor {

    /**
     * Map of directives
     */
    private HashMap<String, String> directives = new HashMap<>();

    /**
     * The current subject term
     */
    private Term currentSubject;

    protected String error = "";
    private final TermFactory termFactory;
    private final AtomFactory atomFactory;
    private final TypeFactory typeFactory;
    private final RDF rdfFactory;

    public TurtleOBDAVisitorImpl(TermFactory termFactory, AtomFactory atomFactory, TypeFactory typeFactory) {
        this.typeFactory = typeFactory;
        this.rdfFactory = new SimpleRDF();
        this.termFactory = termFactory;
        this.atomFactory = atomFactory;
    }

    public String getError() {
        return error;
    }

    private String removeBrackets(String text) {
        return text.substring(1, text.length() - 1);
    }

    private Term typeTerm(String text, IRI datatype) {
        ValueConstant integerConstant = termFactory.getConstantLiteral(text, datatype);
        return termFactory.getTypedTerm(integerConstant, datatype);
    }

    protected Term construct(String text) {
        Term toReturn = null;
        final String PLACEHOLDER = "{}";
        List<Term> terms = new LinkedList<>();
        List<FormatString> tokens = parse(text);
        int size = tokens.size();
        if (size == 1) {
            FormatString token = tokens.get(0);
            if (token instanceof FixedString) {
                ValueConstant uriTemplate = termFactory.getConstantLiteral(token.toString()); // a single URI template
                toReturn = termFactory.getUriTemplate(uriTemplate);
            } else if (token instanceof ColumnString) {
                // a single URI template
                Variable column = termFactory.getVariable(token.toString());
                toReturn = termFactory.getUriTemplate(column);
            }
        } else {
            StringBuilder sb = new StringBuilder();
            for (FormatString token : tokens) {
                if (token instanceof FixedString) { // if part of URI template
                    sb.append(token.toString());
                } else if (token instanceof ColumnString) {
                    sb.append(PLACEHOLDER);
                    Variable column = termFactory.getVariable(token.toString());
                    terms.add(column);
                }
            }
            ValueConstant uriTemplate = termFactory.getConstantLiteral(sb.toString()); // complete URI template
            terms.add(0, uriTemplate);
            toReturn = termFactory.getUriTemplate(terms);
        }
        return toReturn;
    }

    // Column placeholder pattern
    private static final String formatSpecifier = "\\{([^\\}]+)?\\}";
    private static Pattern chPattern = Pattern.compile(formatSpecifier);

    private List<FormatString> parse(String text) {
        List<FormatString> toReturn = new ArrayList<>();
        Matcher m = chPattern.matcher(text);
        int i = 0;
        while (i < text.length()) {
            if (m.find(i)) {
                if (m.start() != i) {
                    toReturn.add(new FixedString(text.substring(i, m.start())));
                }
                String value = m.group(1);
                if (value.contains(".")) {
                    throw new IllegalArgumentException("Fully qualified columns are not accepted.");
                }
                toReturn.add(new ColumnString(value));
                i = m.end();
            } else {
                toReturn.add(new FixedString(text.substring(i)));
                break;
            }
        }
        return toReturn;
    }

    private interface FormatString {
        int index();
        String toString();
    }

    private class FixedString implements FormatString {
        private String s;

        FixedString(String s) {
            this.s = s;
        }

        @Override
        public int index() {
            return -1;
        }  // flag code for fixed string

        @Override
        public String toString() {
            return s;
        }
    }

    private class ColumnString implements FormatString {
        private String s;

        ColumnString(String s) {
            this.s = s;
        }

        @Override
        public int index() {
            return 0;
        }  // flag code for column string

        @Override
        public String toString() {
            return s;
        }
    }

    //this function distinguishes curly bracket with
    //back slash "\{" from curly bracket "{"
    private int getIndexOfCurlyB(String str) {
        int i;
        int j;
        i = str.indexOf("{");
        j = str.indexOf("\\{");
        while ((i - 1 == j) && (j != -1)) {
            i = str.indexOf("{", i + 1);
            j = str.indexOf("\\{", j + 1);
        }
        return i;
    }

    //in case of concat this function parses the literal
    //and adds parsed constant literals and template literal to terms list
    private ArrayList<Term> addToTermsList(String str) {
        ArrayList<Term> terms = new ArrayList<>();
        int i, j;
        String st;
        str = str.substring(1, str.length() - 1);
        while (str.contains("{")) {
            i = getIndexOfCurlyB(str);
            if (i > 0) {
                st = str.substring(0, i);
                st = st.replace("\\\\", "");
                terms.add(termFactory.getConstantLiteral(st));
                str = str.substring(str.indexOf("{", i), str.length());
            } else if (i == 0) {
                j = str.indexOf("}");
                terms.add(termFactory.getVariable(str.substring(1, j)));
                str = str.substring(j + 1, str.length());
            } else {
                break;
            }
        }
        if (!str.equals("")) {
            str = str.replace("\\\\", "");
            terms.add(termFactory.getConstantLiteral(str));
        }
        return terms;
    }

    //this function returns nested concats
    //in case of more than two terms need to be concatted
    private Term getNestedConcat(String str) {
        ArrayList<Term> terms;
        terms = addToTermsList(str);
        if (terms.size() == 1) {
            return terms.get(0);
        }

        Function f = termFactory.getFunction(ExpressionOperation.CONCAT, terms.get(0), terms.get(1));
        for (int j = 2; j < terms.size(); j++) {
            f = termFactory.getFunction(ExpressionOperation.CONCAT, f, terms.get(j));
        }
        return f;
    }

    /**
     * This methods construct an atom from a triple
     * <p>
     * For the input (subject, pred, object), the result is
     * <ul>
     * <li> object(subject), if pred == rdf:type and subject is grounded ; </li>
     * <li> predicate(subject, object), if pred != rdf:type and predicate is grounded ; </li>
     * <li> triple(subject, pred, object), otherwise (it is a higher order atom). </li>
     * </ul>
     */
    private Function makeAtom(Term subject, Term pred, Term object) {
        Function atom;

        if (isRDFType(pred)) {
            if (object instanceof Function) {
                if (QueryUtils.isGrounded(object)) {
                    ValueConstant c = ((ValueConstant) ((Function) object).getTerm(0));  // it has to be a URI constant
                    Predicate predicate = atomFactory.getClassPredicate(c.getValue());
                    atom = termFactory.getFunction(predicate, subject);
                } else {
                    atom = atomFactory.getTripleAtom(subject, pred, object);
                }
            } else if (object instanceof Variable) {
                Term uriOfPred = termFactory.getUriTemplate(pred);
                Term uriOfObject = termFactory.getUriTemplate(object);
                atom = atomFactory.getTripleAtom(subject, uriOfPred, uriOfObject);
            } else {
                throw new IllegalArgumentException("parser cannot handle object " + object);
            }
        } else if (!QueryUtils.isGrounded(pred)) {
            atom = atomFactory.getTripleAtom(subject, pred, object);
        } else {
            Predicate predicate;
            if (pred instanceof Function) {
                ValueConstant pr = (ValueConstant) ((Function) pred).getTerm(0);
                if (object instanceof Variable) {
                    predicate = atomFactory.getDataPropertyPredicate(pr.getValue());
                } else {
                    if (object instanceof Function) {
                        if (((Function) object).getFunctionSymbol() instanceof URITemplatePredicate) {

                            predicate = atomFactory.getObjectPropertyPredicate(pr.getValue());
                        } else {
                            predicate = atomFactory.getDataPropertyPredicate(pr.getValue());
                        }
                    } else {
                        throw new IllegalArgumentException("parser cannot handle object " + object);
                    }
                }
            } else {
                throw new IllegalArgumentException("predicate should be a URI Function");
            }
            atom = termFactory.getFunction(predicate, subject, object);
        }
        return atom;
    }


    private static boolean isRDFType(Term pred) {
        if (pred instanceof Function && ((Function) pred).getTerm(0) instanceof Constant) {
            String c = ((Constant) ((Function) pred).getTerm(0)).getValue();
            return c.equals(RDF_TYPE);
        }
        return false;
    }

    @Override
    public List<Function> visitParse(ParseContext ctx) {
        ctx.directiveStatement().forEach(this::visit);
        return ctx.triplesStatement().stream()
                .flatMap(c -> visitTriplesStatement(c).stream())
                .collect(ImmutableCollectors.toList());
    }

    @Override
    public Void visitDirectiveStatement(DirectiveStatementContext ctx) {
        visit(ctx.directive());
        return null;
    }

    @Override
    public Void visitDirective(DirectiveContext ctx) {
        visit(ctx.prefixID());
        return null;
    }

    @Override
    public List<Function> visitTriplesStatement(TriplesStatementContext ctx) {
        return visitTriples(ctx.triples());
    }

    @Override
    public Void visitPrefixID(PrefixIDContext ctx) {
        String uriref = visitIriref(ctx.iriref());
        String ns = ctx.PNAME_NS().getText();
        directives.put(ns.substring(0, ns.length() - 1), uriref); // remove the end colon
        return null;
    }

    @Override
    public List<Function> visitTriples(TriplesContext ctx) {
        currentSubject = visitSubject(ctx.subject());
        return visitPredicateObjectList(ctx.predicateObjectList());
    }

    @Override
    public List<Function> visitPredicateObjectList(PredicateObjectListContext ctx) {
        return ctx.predicateObject().stream()
                .flatMap(c -> visitPredicateObject(c).stream())
                .collect(ImmutableCollectors.toList());
    }

    @Override
    public List<Function> visitPredicateObject(PredicateObjectContext ctx) {
        return visitObjectList(ctx.objectList()).stream()
                .map(t -> makeAtom(currentSubject, visitVerb(ctx.verb()), t))
                .collect(ImmutableCollectors.toList());
    }

    @Override
    public Term visitVerb(VerbContext ctx) {
        ResourceContext rc = ctx.resource();
        if (rc != null) {
            return visitResource(rc);
        }
        return termFactory.getUriTemplate(termFactory.getConstantLiteral(RDF_TYPE));
    }

    @Override
    public List<Term> visitObjectList(ObjectListContext ctx) {
        return ctx.object().stream()
                .map(this::visitObject)
                .collect(ImmutableCollectors.toList());
    }

    @Override
    public Term visitSubject(SubjectContext ctx) {
        ResourceContext rc = ctx.resource();
        if (rc != null) {
            return visitResource(rc);
        }
        VariableContext vc = ctx.variable();
        if (vc != null) {
            return visitVariable(vc);
        }
        return null;
    }

    @Override
    public Term visitObject(ObjectContext ctx) {
        return (Term) visit(ctx.children.iterator().next());
    }

    @Override
    public Term visitResource(ResourceContext ctx) {
        if (ctx.iriref() != null) {
            return construct(this.visitIriref(ctx.iriref()));
        }
        return construct(this.visitPrefixedName(ctx.prefixedName()));
    }

    public String visitIriref(IrirefContext ctx) {
        return removeBrackets(ctx.IRIREF().getText());
    }

    public String visitPrefixedName(PrefixedNameContext ctx) {
        String[] tokens = ctx.PREFIXEDNAME().getText().split(":", 2);
        String uri = directives.get(tokens[0]);  // the first token is the prefix
        return uri + tokens[1];  // the second token is the local name
    }

    @Override
    public Function visitTypedLiteral_1(TypedLiteral_1Context ctx) {
        return termFactory.getTypedTerm(visitVariable(ctx.variable()), ctx.LANGTAG().getText().substring(1));
    }

    @Override
    public Function visitTypedLiteral_2(TypedLiteral_2Context ctx) {
        Variable var = visitVariable(ctx.variable());
        Term resource = visitResource(ctx.resource());
        if (resource instanceof Function) {
            String functionName = ((ValueConstant) ((Function) resource).getTerm(0)).getValue();

            return typeFactory.getOptionalDatatype(functionName)
                    .map(t -> termFactory.getTypedTerm(var, t))
                    .orElseThrow(() -> new RuntimeException("Unsupported datatype: " + functionName));
        }
        throw new IllegalArgumentException("$resource.value should be an URI");
    }

    @Override
    public Variable visitVariable(VariableContext ctx) {
        return termFactory.getVariable(removeBrackets(ctx.STRING_WITH_CURLY_BRACKET().getText()));
    }

    @Override
    public Function visitFunction(FunctionContext ctx) {
        String functionName = visitResource(ctx.resource()).toString();
        ImmutableList<Term> terms = visitTerms(ctx.terms());
        Predicate functionSymbol = termFactory.getPredicate(functionName, terms.size());
        return termFactory.getFunction(functionSymbol, terms);
    }

    @Override
    public ImmutableList<Term> visitTerms(TermsContext ctx) {
        return ctx.term().stream()
                .map(this::visitTerm)
                .collect(ImmutableCollectors.toList());
    }

    @Override
    public Term visitTerm(TermContext ctx) {
        return (Term) visitChildren(ctx);
    }

    @Override
    public Term visitLiteral(LiteralContext ctx) {
        StringLiteralContext slc = ctx.stringLiteral();
        if (slc != null) {
            Term literal = visitStringLiteral(slc);
            TerminalNode token = ctx.LANGTAG();
            //if variable we cannot assign a datatype yet
            if (literal instanceof Variable) {
                return termFactory.getTypedTerm(literal, XSD.STRING);
            }
            if (token != null) {
                return termFactory.getTypedTerm(literal, token.getText().substring(1));
            }
            return termFactory.getTypedTerm(literal, XSD.STRING);
        }
        return (Term) visitChildren(ctx);
    }

    @Override
    public Term visitStringLiteral(StringLiteralContext ctx) {
        String str = ctx.STRING_LITERAL_QUOTE().getText();
        if (str.contains("{")) {
            return getNestedConcat(str);
        }
        return termFactory.getConstantLiteral(str.substring(1, str.length() - 1), XSD.STRING); // without the double quotes
    }

    @Override
    public Term visitDataTypeString(DataTypeStringContext ctx) {
        Term stringValue = visitStringLiteral(ctx.stringLiteral());
        Term resource = visitResource(ctx.resource());
        if (resource instanceof Function) {
            String functionName = ((ValueConstant) ((Function) resource).getTerm(0)).getValue();

            Optional<RDFDatatype> type = typeFactory.getOptionalDatatype(functionName);
            if (!type.isPresent()) {
                throw new RuntimeException("Unsupported datatype: " + functionName);
            }
            return termFactory.getTypedTerm(stringValue, type.get());
        }
        return termFactory.getTypedTerm(stringValue, XSD.STRING);
    }

    @Override
    public Term visitNumericLiteral(NumericLiteralContext ctx) {
        return (Term) visitChildren(ctx);
    }

    @Override
    public Term visitBooleanLiteral(BooleanLiteralContext ctx) {
        return typeTerm(ctx.BOOLEAN_LITERAL().getText(), XSD.BOOLEAN);
    }

    @Override
    public Term visitNumericUnsigned(NumericUnsignedContext ctx) {

        TerminalNode token = ctx.INTEGER();
        if (token != null) {
            return typeTerm(token.getText(), XSD.INTEGER);
        }
        token = ctx.DOUBLE();
        if (token != null) {
            return typeTerm(token.getText(), XSD.DOUBLE);
        }
        return typeTerm(ctx.DECIMAL().getText(), XSD.DECIMAL);
    }

    @Override
    public Term visitNumericPositive(NumericPositiveContext ctx) {
        TerminalNode token = ctx.INTEGER_POSITIVE();
        if (token != null) {
            return typeTerm(token.getText(), XSD.INTEGER);
        }
        token = ctx.DOUBLE_POSITIVE();
        if (token != null) {
            return typeTerm(token.getText(), XSD.DOUBLE);
        }
        return typeTerm(ctx.DECIMAL_POSITIVE().getText(), XSD.DECIMAL);
    }

    @Override
    public Term visitNumericNegative(NumericNegativeContext ctx) {
        TerminalNode token = ctx.INTEGER_NEGATIVE();
        if (token != null) {
            return typeTerm(token.getText(), XSD.INTEGER);
        }
        token = ctx.DOUBLE_NEGATIVE();
        if (token != null) {
            return typeTerm(token.getText(), XSD.DOUBLE);
        }
        return typeTerm(ctx.DECIMAL_NEGATIVE().getText(), XSD.DECIMAL);
    }
}