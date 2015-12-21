package tool.analyzers.functional;

import jadd.ADD;
import jadd.JADD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.analyzers.ADDReliabilityResults;
import tool.analyzers.IPruningStrategy;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.NoPruningStrategy;
import tool.analyzers.buildingblocks.AssetProcessor;
import tool.analyzers.buildingblocks.Component;
import tool.analyzers.buildingblocks.DerivationFunction;
import tool.stats.CollectibleTimers;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;
import expressionsolver.Expression;
import expressionsolver.ExpressionSolver;

public class FeatureFamilyBasedAnalyzer {

    private ADD featureModel;
    private JADD jadd;
    private ExpressionSolver expressionSolver;
    private IPruningStrategy pruningStrategy;

    private FeatureBasedFirstPhase firstPhase;

    /**
     * Sigma_v
     */
    private DerivationFunction<ADD, Expression<ADD>, ADD> solve;


    private ITimeCollector timeCollector;

    public FeatureFamilyBasedAnalyzer(JADD jadd,
                                      ADD featureModel,
                                      ParametricModelChecker modelChecker,
                                      ITimeCollector timeCollector,
                                      IFormulaCollector formulaCollector) {
        this.expressionSolver = new ExpressionSolver(jadd);
        this.jadd = jadd;
        this.featureModel = featureModel;

        this.timeCollector = timeCollector;
        this.pruningStrategy = new NoPruningStrategy();

        this.firstPhase = new FeatureBasedFirstPhase(modelChecker,
                                                     formulaCollector);

        AssetProcessor<Expression<ADD>, ADD> evalAndPrune = (expr, values) -> {
            return this.pruningStrategy.pruneInvalidConfigurations(null,
                                                                   expr.solve(values),
                                                                   featureModel);
        };
        solve = DerivationFunction.abstractDerivation(ADD::ite,
                                                      evalAndPrune,
                                                      jadd.makeConstant(1.0));
    }

    /**
     * Evaluates the feature-family-based reliability function of an RDG node, based
     * on the reliabilities of the nodes on which it depends.
     *
     * A reliability function is a boolean function from the set of features
     * to Real values, where the reliability of any invalid configuration is 0.
     *
     * @param node RDG node whose reliability is to be evaluated.
     * @param dotOutput path at where to dump the resulting ADD as a dot file.
     * @return
     * @throws CyclicRdgException
     */
    public IReliabilityAnalysisResults evaluateReliability(RDGNode node, String dotOutput) throws CyclicRdgException {
        List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();

        timeCollector.startTimer(CollectibleTimers.FEATURE_BASED_TIME);
        // Alpha_v
        List<Component<String>> expressions = firstPhase.getReliabilityExpressions(dependencies);
        timeCollector.stopTimer(CollectibleTimers.FEATURE_BASED_TIME);

        timeCollector.startTimer(CollectibleTimers.FAMILY_BASED_TIME);
        // Lift
        List<Component<Expression<ADD>>> liftedExpressions = expressions.stream()
                .map(c -> c.map(expressionSolver::parseExpressionForFunctions))
                .collect(Collectors.toList());

        ADD reliability = solveFromMany(liftedExpressions);
        ADD result = featureModel.times(reliability);
        timeCollector.stopTimer(CollectibleTimers.FAMILY_BASED_TIME);

        if (dotOutput != null) {
            generateDotFile(result, dotOutput);
        }

        return new ADDReliabilityResults(result);
    }

    /**
     * Sets the pruning strategy to be used for preventing calculation
     * of reliability values for invalid configurations.
     *
     * If none is set, the default behavior is to multiply the reliability
     * mappings by the feature model's 0,1-ADD (so that valid configurations
     * yield the same reliability, but invalid ones yield 0).
     *
     * @param pruningStrategy the pruningStrategy to set
     */
    public void setPruningStrategy(IPruningStrategy pruningStrategy) {
        this.pruningStrategy = pruningStrategy;
    }

    /**
     * Dumps the computed family reliability function to the output file
     * in the specified path.
     *
     * @param familyReliability Reliability function computed by a call to the
     *          {@link #evaluateFeatureFamilyBasedReliability(RDGNode)} method.
     * @param outputFile Path to the .dot file to be generated.
     */
    public void generateDotFile(ADD familyReliability, String outputFile) {
        jadd.dumpDot("Family Reliability", familyReliability, outputFile);
    }

    // TODO Candidate!
    private ADD solveFromMany(List<Component<Expression<ADD>>> dependencies) {
        Map<String, ADD> reliabilities = new HashMap<String, ADD>();
        return dependencies.stream()
                .map(c -> deriveSingle(c, solve, reliabilities))
                .reduce((first, actual) -> actual)
                .get();
    }

    // TODO Candidate!
    private ADD deriveSingle(Component<Expression<ADD>> component, DerivationFunction<ADD, Expression<ADD>, ADD> derive, Map<String, ADD> derivedModels) {
        ADD presence = expressionSolver.encodeFormula(component.getPresenceCondition());
        ADD derived = derive.apply(presence, component.getAsset(), derivedModels);
        derivedModels.put(component.getId(), derived);
        return derived;
    }
}
