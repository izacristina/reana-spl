package paramwrapper;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import fdtmc.FDTMC;
import fdtmc.State;
import fdtmc.Transition;

class Command {
	private int initialState;
	private List<String> updatesProbabilities;
	private List<Integer> updatesActions;

	public Command(int initialState) {
		this.initialState = initialState;
        this.updatesProbabilities = new LinkedList<String>();
        this.updatesActions = new LinkedList<Integer>();
	}

	public void addUpdate(String probability, int update) {
		updatesProbabilities.add(probability);
		updatesActions.add(update);
	}

	public Collection<String> getUpdatesProbabilities() {
		return updatesProbabilities;
	}
	
	private Command generateCommand(Entry<State, List<Transition>> entry, int initState) {
		Command command = new Command(initState);
		if (entry.getValue() != null) {
		    for (Transition transition : entry.getValue()) {
		        command.addUpdate(transition.getProbability(),
		                          transition.getTarget().getIndex());
		    }
		} else {
		    // Workaround: manually adding self-loops in case no
		    // transition was specified for a given state.
		    command.addUpdate("1", initState);
		}
		
		return command;
	}
	
	public Map<Integer, Command> getCommands(FDTMC fdtmc) {
		Map<Integer, Command> tmpCommands = new TreeMap<Integer, Command>();
		for (Entry<State, List<Transition>> entry : fdtmc.getTransitions().entrySet()) {
		    int initState = entry.getKey().getIndex();
		    
		    Command command = generateCommand(entry, initState);
		    
			tmpCommands.put(initState, command);
		}
		return tmpCommands;
	}

	public String makeString(String stateVariable) {
		String command = "[] "+stateVariable+"="+initialState+" -> ";
		boolean needsPlus = false;
		for (int i = 0; i < updatesProbabilities.size(); i++) {
		    if (needsPlus) {
		        command += " + ";
		    } else {
		        needsPlus = true;
		    }
			command += "("+updatesProbabilities.get(i)+") : ("+stateVariable+"'="+updatesActions.get(i)+")";
		}
		return command+";";
	}
}
