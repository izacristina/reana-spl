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
import junit.framework.Assert;



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
			setStateVariable(fdtmc.getVariableName());
		}
		setInitialState(fdtmc.getInitialState().getIndex());
		
		setCommands(new Command(initialState).getCommands(fdtmc));
		
		setLabels(getLabels(fdtmc));
		setStateRangeStart(Collections.min(commands.keySet()));
		// PARAM não deixa declarar um intervalo com apenas um número.
		setStateRangeEnd(Math.max(stateRangeStart + 1,
				 Collections.max(commands.keySet())));
		setParameters(getParameters(commands.values()));
	}
	
	private void setCommands(Map<Integer, Command> commands) {
		this.commands = commands;
	}
	
	private void setLabels(Map<String, Set<Integer>> labels) {
		this.labels = labels;
	}
	
	private void setParameters(Set<String> parameters) {
		this.parameters = parameters;
	}
	
	private void setStateRangeEnd(int stateRangeEnd) {
		this.stateRangeEnd = stateRangeEnd;
	}
	
	private void setStateRangeStart(int stateRangeStart) {
		this.stateRangeStart = stateRangeStart;
	}
	
	private void setStateVariable(String stateVariable) {
		this.stateVariable = stateVariable;
	}
	
	private void setInitialState(int state) {
		this.initialState = state;
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
		
		Assert.assertNotNull(states);
		
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
			module += "label \""+entry.getKey()+"\" = ";

			Set<Integer> states = entry.getValue();
			
			Assert.assertNotNull(states);
			
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