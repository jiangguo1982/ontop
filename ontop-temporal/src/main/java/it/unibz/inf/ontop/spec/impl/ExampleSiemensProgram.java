package it.unibz.inf.ontop.temporal.model.impl;

import it.unibz.inf.ontop.model.OntopModelSingletons;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.term.functionsymbol.ExpressionOperation;
import it.unibz.inf.ontop.model.term.functionsymbol.Predicate;
import it.unibz.inf.ontop.temporal.model.*;

import java.time.Duration;

public class ExampleSiemensProgram {

    public static DatalogMTLProgram getSampleProgram(){
        DatalogMTLFactory f = DatalogMTLFactoryImpl.getInstance();
        TermFactory odf = OntopModelSingletons.TERM_FACTORY;

        TemporalRange rangeLRS = f.createTemporalRange(false, true, Duration.parse("PT0M"), Duration.parse("PT1M"));
        TemporalRange rangeHRS = f.createTemporalRange(false, true, Duration.parse("PT0S"), Duration.parse("PT30S"));
        TemporalRange rangeMFON = f.createTemporalRange(false, true, Duration.parse("PT0S"), Duration.parse("PT10S"));
        TemporalRange rangeDiamondInner = f.createTemporalRange(false, true, Duration.parse("PT0M"), Duration.parse("PT2M"));
        TemporalRange rangeDiamondOuter = f.createTemporalRange(false, true, Duration.parse("PT0M"), Duration.parse("PT10M"));

        final Predicate conceptLRS = odf.getClassPredicate("LowRotorSpeed");
        final Predicate conceptHRS = odf.getClassPredicate("HighRotorSpeed");
        final Predicate conceptMFON = odf.getClassPredicate("MainFlameOn");
        final Predicate dataPropertyRs = odf.getObjectPropertyPredicate("rotorSpeed");
        final Predicate conceptPIO = odf.getClassPredicate("PurgingIsOver");

        final Predicate conceptTurbine = odf.getClassPredicate("Turbine");
        final Predicate conceptTempSensor = odf.getClassPredicate("TemperatureSensor");
        final Predicate conceptRotSpeedSensor = odf.getClassPredicate("RotationSpeedSensor");
        final Predicate objectPropertyIMB = odf.getObjectPropertyPredicate("isMonitoredBy");
        final Predicate objectPropertyIPO = odf.getObjectPropertyPredicate("isPartOf");
        final Predicate conceptCLTRS = odf.getClassPredicate("ColocTempRotSensors");

        final Variable varRs = odf.getVariable("rs");
        final Variable varTs = odf.getVariable("ts");
        final Variable varTb = odf.getVariable("tb");
        final Variable varV = odf.getVariable("v");
        final Variable varPt = odf.getVariable("pt");
        final Variable varBurner = odf.getVariable("b");

        TemporalAtomicExpression lrs = f.createTemporalAtomicExpression(conceptLRS, varRs);
        TemporalAtomicExpression rs = f.createTemporalAtomicExpression(dataPropertyRs, varTb, varV);
        TemporalAtomicExpression comparisonLs = f.createTemporalAtomicExpression(ExpressionOperation.LT, varV, odf.getConstantLiteral("1000", Predicate.COL_TYPE.DECIMAL));
        TemporalAtomicExpression hrs = f.createTemporalAtomicExpression(conceptHRS, varRs);
        TemporalAtomicExpression comparisonHs = f.createTemporalAtomicExpression(ExpressionOperation.GT, varV, odf.getConstantLiteral("1260", Predicate.COL_TYPE.DECIMAL));
        TemporalAtomicExpression mfon = f.createTemporalAtomicExpression(conceptMFON, varTs);
        TemporalAtomicExpression pio = f.createTemporalAtomicExpression(conceptPIO, varTb);

        TemporalAtomicExpression tb = f.createTemporalAtomicExpression(conceptTurbine, varTb);
        TemporalAtomicExpression ts = f.createTemporalAtomicExpression(conceptTempSensor, varTs);
        TemporalAtomicExpression rss = f.createTemporalAtomicExpression(conceptRotSpeedSensor, varRs);
        TemporalAtomicExpression isMonitoredByTs = f.createTemporalAtomicExpression(objectPropertyIMB, varBurner, varTs);
        TemporalAtomicExpression isMonitoredByRS = f.createTemporalAtomicExpression(objectPropertyIMB, varPt, varRs);
        TemporalAtomicExpression isPartOfPt = f.createTemporalAtomicExpression(objectPropertyIPO, varPt, varTb);
        TemporalAtomicExpression isPartOfB = f.createTemporalAtomicExpression(objectPropertyIPO, varBurner, varTb);
        TemporalAtomicExpression CLTRS = f.createTemporalAtomicExpression(conceptCLTRS, varTb, varTs, varRs);

        TemporalExpression boxMinusLRS = f.createBoxMinusExpression(rangeLRS,lrs);
        TemporalExpression diamondMinusLRS = f.createDiamondMinusExpression(rangeDiamondInner, boxMinusLRS);
        TemporalExpression boxMinusHRS = f.createBoxMinusExpression(rangeHRS, hrs);
        TemporalExpression innerExp = f.createTemporalJoinExpression(boxMinusHRS, diamondMinusLRS);
        TemporalExpression diamondInnerExp = f.createDiamondMinusExpression(rangeDiamondOuter, innerExp);
        TemporalExpression boxMinusMFON = f.createBoxMinusExpression(rangeMFON, mfon);
        TemporalExpression temporalPIO = f.createTemporalJoinExpression(boxMinusMFON, diamondInnerExp);
        TemporalExpression bodyCLTRS = f.createTemporalJoinExpression(tb, ts, rss, isMonitoredByRS, isMonitoredByTs, isPartOfPt, isPartOfB);
        TemporalExpression bodyPIO = f.createTemporalJoinExpression(temporalPIO, CLTRS);

        DatalogMTLRule CLTRSrule = f.createRule(CLTRS, bodyCLTRS);
        DatalogMTLRule LRSrule = f.createRule(lrs, f.createTemporalJoinExpression(rs, comparisonLs));
        DatalogMTLRule HRSrule = f.createRule(hrs, f.createTemporalJoinExpression(rs, comparisonHs));
        DatalogMTLRule PIOrule = f.createRule(pio, bodyPIO);

        return f.createProgram(LRSrule, HRSrule, CLTRSrule, PIOrule);
    }
}
