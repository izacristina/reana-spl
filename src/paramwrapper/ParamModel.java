package paramwrapper;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fdtmc.FDTMC;
import fdtmc.State;
import fdtmc.Transition;



class ParamModel {
	private String stateVariable = "s";
	// TODO Deixar nome do módulo PARAM configurável.
	private String moduleName = "dummyModule";
	// TODO Inferir estado inicial a partir da topologia da FDTMC.
	private int initialState = 0;

	private Set<String> parameters;
	private Map<String, Set<Integer>> labels;
	private Map<Integer, Command> commands;

	private int stateRangeStart;
	private int stateRangeEnd;

	public ParamModel(FDTMC fdtmc) {
		if (fdtmc.getVariableName() != null) {
			stateVariable = fdtmc.getVariableName();
		}
		initialState = fdtmc.getInitialState().getIndex();
		
		commands = new Command(initialState).getCommands(fdtmc);
		
		labels = getLabels(fdtmc);
		stateRangeStart = Collections.min(commands.keySet());
		// PARAM não deixa declarar um intervalo com apenas um número.
		stateRangeEnd = Math.max(stateRangeStart + 1,
								 Collections.max(commands.keySet()));
		parameters = getParameters(commands.values());
	}

    public int getParametersNumber() {
        return parameters.size();
    }

    public Set<String> getParameters() {
        return parameters;
    }

	public int getStatesNumber() {
	    return stateRangeEnd+1;
	}

	private Map<String, Set<Integer>> getLabels(FDTMC fdtmc) {
		Map<String, Set<Integer>> labeledStates = new TreeMap<String, Set<Integer>>();
		Collection<State> states = fdtmc.getStates();
		for (State s : states) {
			String label = s.getLabel();
			if (label != null && !label.isEmpty()) {
				if (!labeledStates.containsKey(label)) {
					labeledStates.put(label, new TreeSet<Integer>());
				}
				labeledStates.get(label).add(s.getIndex());
			}
		}
		return labeledStates;
	}

	private Set<String> getParameters(Collection<Command> commands) {
		Set<String> tmpParameters = new HashSet<String>();

		Pattern validIdentifier = Pattern.compile("(^|\\d+-)([A-Za-z_][A-Za-z0-9_]*)");
		for (Command command : commands) {
			for (String probability : command.getUpdatesProbabilities()) {
				Matcher m = validIdentifier.matcher(probability);
				while (m.find()) {
					tmpParameters.add(m.group(2));
				}
			}
		}
		return tmpParameters;
	}
	
	private String labelsToModule() {
		String module = "";
		
		for (Map.Entry<String, Set<Integer>> entry : labels.entrySet()) {
			String label = entry.getKey();
			module += "label \""+label+"\" = ";

			Set<Integer> states = entry.getValue();
			int count = 1;
			for (Integer state : states) {
				module += stateVariable+"="+state;
				if (count < states.size()) {
					module += " | ";
				}
				count++;
			}
			module += ";\n";
		}
		
		return module;
	}

	@Override
	public String toString() {
		String params = "";
		for (String parameter : parameters) {
			params += "param double "+parameter+";\n";
		}
		String module =
				"dtmc\n" +
				"\n" +
				params +
				"\n" +
				"module " + moduleName + "\n" +
				"	"+stateVariable+ " : ["+stateRangeStart+".."+stateRangeEnd+"] init "+initialState+";" +
				"\n";
		for (Command command : commands.values()) {
			module += "	"+command.makeString(stateVariable) + "\n";
		}
		module += "endmodule\n\n";
		module += labelsToModule();
		
		return module;
	}
}