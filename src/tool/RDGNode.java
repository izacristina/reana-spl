package tool;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import tool.analyzers.buildingblocks.Component;
import fdtmc.FDTMC;


public class RDGNode {

	private static final int _PathToItSelf = 1;
	//This reference is used to store all the RDGnodes created during the evaluation
	private static Map<String, RDGNode> rdgNodes = new HashMap<String, RDGNode>();
	private static List<RDGNode> nodesInCreationOrder = new LinkedList<RDGNode>();

    private static int lastNodeIndex = 0;

	// Node identifier
	private String id;
	//This attribute is used to store the FDTMC for the RDG node.
	private FDTMC fdtmc;
	/**
	 * The node must have an associated presence condition, which is
	 * a boolean expression over features.
	 */
	private String presenceCondition;
	// Nodes on which this one depends
	private Collection<RDGNode> dependencies;
	/**
	 * Height of the RDGNode.
	 */
	private int height;


	/**
	 * The id, presence condition and model (FDTMC) of an RDG node must
	 * be immutable, so there must be no setters for them. Hence, they
	 * must be set at construction-time.
	 *
	 * @param id Node's identifier. It is preferably a valid Java identifier.
	 * @param presenceCondition Boolean expression over features (using Java operators).
	 * @param fdtmc Stochastic model of the piece of behavioral model represented by
	 *             this node.
	 */
	public RDGNode(String id, String presenceCondition, FDTMC fdtmc) {
	    this.id = id;
	    this.presenceCondition = presenceCondition;
	    this.fdtmc = fdtmc;
		this.dependencies = new HashSet<RDGNode>();
		this.height = 0;

		rdgNodes.put(id, this);
		nodesInCreationOrder.add(this);
	}

    public FDTMC getFDTMC() {
        return this.fdtmc;
    }

    public void addDependency(RDGNode child) {
        this.dependencies.add(child);
         setHeight(Math.max(height, child.height + 1));
    }

    public Collection<RDGNode> getDependencies() {
        return dependencies;
    }

    public String getPresenceCondition() {
        return presenceCondition;
    }

    public String getId() {
        return id;
    }

    /**
     * Height of the RDGNode. This metric is defined in the same way as
     * the height of a tree node, i.e., the maximum number of nodes in a path
     * from this one to a leaf (node with no dependencies).
     */
    public int getHeight() {
        return height;
    }
    public void setHeight(int height){
    	this.height = height;
    }

    public static RDGNode getById(String id) {
        return rdgNodes.get(id);
    }

    public static String getNextId() {
        return "n" + lastNodeIndex++;
    }

    /**
     * We consider two RDG nodes to be equal whenever their behavior is
     * modeled by equal FDTMCs, their presence condition is the same and
     * their dependencies are also correspondingly equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (isObjectValid(obj)) {
            RDGNode other = (RDGNode) obj;
            
            final boolean presenceEquals = this.getPresenceCondition().equals(other.getPresenceCondition());
            final boolean FDTMCEquals = this.getFDTMC().equals(other.getFDTMC());
            final boolean dependenciesEquals = this.getDependencies().equals(other.getDependencies());
            
            boolean isEquals = presenceEquals && FDTMCEquals && dependenciesEquals; 
            return isEquals;
        }
        return false;
    }
    private boolean isObjectValid(Object obj){
    	return obj != null && obj instanceof RDGNode;
    }
    @Override
    public int hashCode() {
        int hashedCode = getId().hashCode() + getPresenceCondition().hashCode() + getFDTMC().hashCode() + getDependencies().hashCode();
        return hashedCode;
    }

    @Override
    public String toString() {
        String presenceCondition = getId() + " (" + getPresenceCondition() + ")";
        return presenceCondition;
    }

    /**
     * Retrieves the transitive closure of the RDGNode dependency relation.
     * The node itself is part of the returned list.
     *
     * It implements the Cormen et al.'s topological sort algorithm.
     *
     * @return The descendant RDG nodes ordered bottom-up (depended-upon to dependent).
     * @throws CyclicRdgException if there is a path with a cycle starting from this node.
     */
    public List<RDGNode> getDependenciesTransitiveClosure() throws CyclicRdgException {
        List<RDGNode> transitiveDependencies = new LinkedList<RDGNode>();
        Map<RDGNode, Boolean> marks = new HashMap<RDGNode, Boolean>();
        topoSortVisit(this, marks, transitiveDependencies);
        return transitiveDependencies;
    }

    /**
     * Topological sort {@code visit} function (Cormen et al.'s algorithm).
     * @param node
     * @param marks
     * @param sorted
     * @throws CyclicRdgException
     */
    private void topoSortVisit(RDGNode node, Map<RDGNode, Boolean> marks, List<RDGNode> sorted) throws CyclicRdgException {
        if (isCyclicDependency(node, marks)) {
            // Visiting temporarily marked node -- this means a cyclic dependency!
            throw new CyclicRdgException();
        } else if (nodeNotVisited(node, marks)) {
            // Mark node temporarily (cycle detection)
            marks.put(node, false);
            for (RDGNode child: node.getDependencies()) {
                topoSortVisit(child, marks, sorted);
            }
            // Mark node permanently (finished sorting branch)
            marks.put(node, true);
            sorted.add(node);
        }
    }

	private static boolean isCyclicDependency(RDGNode node, Map<RDGNode, Boolean> marks) {
		return marks.containsKey(node) && marks.get(node) == false;
	}

    /**
     * Computes the number of paths from source nodes to every known node.
     * @return A map associating an RDGNode to the corresponding number
     *      of paths from a source node which lead to it.
     * @throws CyclicRdgException
     */
    public Map<RDGNode, Integer> getNumberOfPaths() throws CyclicRdgException {
        Map<RDGNode, Integer> numberOfPaths = new HashMap<RDGNode, Integer>();

        Map<RDGNode, Boolean> marks = new HashMap<RDGNode, Boolean>();
        Map<RDGNode, Map<RDGNode, Integer>> cache = new HashMap<RDGNode, Map<RDGNode,Integer>>();
        Map<RDGNode, Integer> tmpNumberOfPaths = numPathsVisit(this, marks, cache);
        numberOfPaths = sumPaths(numberOfPaths, tmpNumberOfPaths);

        return numberOfPaths;
    }

    // TODO Parameterize topological sort of RDG.
    private static Map<RDGNode, Integer> numPathsVisit(RDGNode node, Map<RDGNode, Boolean> marks, Map<RDGNode, Map<RDGNode, Integer>> cache) throws CyclicRdgException {
        if (isCyclicDependency(node, marks)) {
            // Visiting temporarily marked node -- this means a cyclic dependency!
            throw new CyclicRdgException();
        } else if (nodeNotVisited(node, marks)) {
            Map<RDGNode, Integer> numberOfPaths = topologicalSort(node, marks, cache);
            return numberOfPaths;
        }
        // Otherwise, the node has already been visited.
        return cache.get(node);
    }

	private static boolean nodeNotVisited(RDGNode node, Map<RDGNode, Boolean> marks) {
		return !marks.containsKey(node);
	}

	private static Map<RDGNode, Integer> topologicalSort(RDGNode node, Map<RDGNode, Boolean> marks,
			Map<RDGNode, Map<RDGNode, Integer>> cache) {
		marks.put(node, false);

		Map<RDGNode, Integer> numberOfPaths = new HashMap<RDGNode, Integer>();
		// A node always has a path to itself.
		numberOfPaths.put(node, _PathToItSelf);
		// The number of paths from a node X to a node Y is equal to the
		// sum of the numbers of paths from each of its descendants to Y.
		for (RDGNode child: node.getDependencies()) {
		    Map<RDGNode, Integer> tmpNumberOfPaths = numPathsVisit(child, marks, cache);
		    numberOfPaths = sumPaths(numberOfPaths, tmpNumberOfPaths);
		}
		// Mark node permanently (finished sorting branch)
		marks.put(node, true);
		cache.put(node, numberOfPaths);
		return numberOfPaths;
	}

    /**
     * Sums two paths-counting maps
     * @param pathsCountA
     * @param pathsCountB
     * @return
     */
    private static Map<RDGNode, Integer> sumPaths(Map<RDGNode, Integer> pathsCountA, Map<RDGNode, Integer> pathsCountB) {
        Map<RDGNode, Integer> numberOfPaths = new HashMap<RDGNode, Integer>(pathsCountA);
        for (Map.Entry<RDGNode, Integer> entry: pathsCountB.entrySet()) {
            RDGNode node = entry.getKey();
            Integer count = entry.getValue();
            if (numberOfPaths.containsKey(node)) {
                count += numberOfPaths.get(node);
            }
            numberOfPaths.put(node, count);
        }
        return numberOfPaths;
    }

    /**
     * Returns the first RDG node (in crescent order of creation time) which is similar
     * to the one provided.
     *
     * A similar RDG node is one for which equals() returns true.
     * @param rdgNode
     * @return a similar RDG node or null in case there is none.
     */
    public static RDGNode getSimilarNode(RDGNode target) {
        for (RDGNode candidate: nodesInCreationOrder) {
            if (isNoteSimilar(candidate, target)) {
                return candidate;
            }
        }
        return null;
    }
    private static boolean isNoteSimilar(RDGNode candidate, RDGNode target){
    	return candidate != target && candidate.equals(target);
    }
    /**
     * Converts this RDG node into a Component<FDTMC>.
     * @return
     */
    public Component<FDTMC> toComponent() {
        Collection<Component<FDTMC>> dependencies = this.getDependencies().stream()
                .map(RDGNode::toComponent)
                .collect(Collectors.toSet());
        Component<FDTMC> component = new Component<FDTMC>(this.getId(),
                                    this.getPresenceCondition(),
                                    this.getFDTMC(),
                                    dependencies);
        return component;
    }

    public static List<Component<FDTMC>> toComponentList(List<RDGNode> nodes) {
        List<Component<FDTMC>> nodeList = nodes.stream()
                .map(RDGNode::toComponent)
                .collect(Collectors.toList());
        return nodeList;
    }

}
