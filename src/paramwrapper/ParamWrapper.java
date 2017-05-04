/**
 *
 */
package paramwrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import fdtmc.FDTMC;
import junit.framework.Assert;

/**
 * Façade to a PARAM executable.
 *
 * @author Thiago
 *
 */
public class ParamWrapper implements ParametricModelChecker {
	private static final Logger LOGGER = Logger.getLogger(ParamWrapper.class.getName());

	private String paramPath;
	private IModelCollector modelCollector;
	private boolean usePrism = false;

	public ParamWrapper(String paramPath) {
		this(paramPath, new NoopModelCollector());
	}

	public ParamWrapper(String paramPath, IModelCollector modelCollector) {
		setParamPath(paramPath);
		setUsePrism(paramPath.contains("prism"));
		setModelCollector(modelCollector);
	}
	
	public boolean getUsePrism(){
		return usePrism;
	}
	
	public void setUsePrism(boolean usePrism){
		this.usePrism = usePrism;
	}
	
	public IModelCollector getModelCollector(){
		return modelCollector;
	}
	
	public void setModelCollector(IModelCollector modelCollector){
		this.modelCollector = modelCollector;
	}
	
	public String getParamPath(){
		return paramPath;
	}
	
	public void setParamPath(String paramPath){
		this.paramPath = paramPath;
	}

	public String fdtmcToParam(FDTMC fdtmc) {
		ParamModel model = new ParamModel(fdtmc);
		getModelCollector().collectModel(model.getParametersNumber(), model.getStatesNumber());
		return model.toString();
	}

	@Override
	public String getReliability(FDTMC fdtmc) {
		ParamModel model = new ParamModel(fdtmc);
		getModelCollector().collectModel(model.getParametersNumber(), model.getStatesNumber());
		String modelString = model.toString();

		if (getUsePrism()) {
			modelString = modelString.replace("param", "const");
		}
		String reliabilityProperty = "P=? [ F \"success\" ]";

		return evaluate(modelString, reliabilityProperty, model);
	}

	private File writeFile(String strToBeWritten, String prefix, String suffix) throws IOException {
		File file = File.createTempFile(prefix, suffix);
		FileWriter writer = new FileWriter(file);
		
		Assert.assertNotNull(writer);
		
		writer.write(strToBeWritten);
		writer.flush();
		writer.close();

		return file;
	}

	private String writeFormula(File modelFile, File propertyFile, File resultsFile, 
			String modelString, ParamModel model) throws IOException {
		String formula;
		long startTime = System.nanoTime();
		if (getUsePrism()) {
			if(!modelString.contains("const")) {
				formula = invokeModelChecker(modelFile.getAbsolutePath(),
						propertyFile.getAbsolutePath(),
						resultsFile.getAbsolutePath());
			}
			else{
				formula = invokeParametricPRISM(model,
						modelFile.getAbsolutePath(),
						propertyFile.getAbsolutePath(),
						resultsFile.getAbsolutePath());
			}
		} else {
			formula = invokeParametricModelChecker(modelFile.getAbsolutePath(),
					propertyFile.getAbsolutePath(),
					resultsFile.getAbsolutePath());
		}
		long elapsedTime = System.nanoTime() - startTime;
		getModelCollector().collectModelCheckingTime(elapsedTime);
		
		Assert.assertNotNull(formula);
		
		return formula.trim().replaceAll("\\s+", "");
	}

	private String evaluate(String modelString, String property, ParamModel model) {
		try {
			LOGGER.finer(modelString);

			File modelFile = writeFile(modelString, "model", "param");

			File propertyFile = writeFile(property, "property", "prop");

			File resultsFile = File.createTempFile("result", null);

			String formula = writeFormula(modelFile, propertyFile, resultsFile, modelString, model);

			return formula;
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
		}
		return "";
	}

	private String invokeParametricModelChecker(String modelPath,
			String propertyPath,
			String resultsPath) throws IOException {
		String commandLine = getParamPath()+" "
				+modelPath+" "
				+propertyPath+" "
				+"--result-file "+resultsPath;
		return invokeAndGetResult(commandLine, resultsPath+".out");
	}

	private String invokeParametricPRISM(ParamModel model,
			String modelPath,
			String propertyPath,
			String resultsPath) throws IOException {
		String commandLine = getParamPath()+" "
				+modelPath+" "
				+propertyPath+" "
				+"-exportresults "+resultsPath+" "
				+"-param "+String.join(",", model.getParameters());
		String rawResult = invokeAndGetResult(commandLine, resultsPath);
		
		Assert.assertNotNull(rawResult);
		
		int openBracket = rawResult.indexOf("{");
		int closeBracket = rawResult.indexOf("}");
		String expression = rawResult.substring(openBracket+1, closeBracket);
		
		Assert.assertNotNull(expression);
		
		return expression.trim().replace('|', '/');
	}

	private String invokeModelChecker(String modelPath,
			String propertyPath,
			String resultsPath) throws IOException {
		String commandLine = getParamPath()+" "
				+modelPath+" "
				+propertyPath+" "
				+"-exportresults "+resultsPath;
		return invokeAndGetResult(commandLine, resultsPath);
	}

	private String invokeAndGetResult(String commandLine, String resultsPath) throws IOException {
		LOGGER.fine(commandLine);
		Process program = Runtime.getRuntime().exec(commandLine);
		int exitCode = 0;
		try {
			Assert.assertNotNull(program);
			exitCode = program.waitFor();
		} catch (InterruptedException e) {
			LOGGER.severe("Exit code: " + exitCode);
			LOGGER.log(Level.SEVERE, e.toString(), e);
		}
		List<String> lines = Files.readAllLines(Paths.get(resultsPath), Charset.forName("UTF-8"));
		Assert.assertNotNull(lines);
		lines.removeIf(String::isEmpty);
		// Formula		
		return lines.get(lines.size()-1);
	}

}
