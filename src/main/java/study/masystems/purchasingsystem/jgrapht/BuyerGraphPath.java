package study.masystems.purchasingsystem.jgrapht;

import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;

import java.util.ArrayList;
import java.util.List;

public class BuyerGraphPath<V, E> implements GraphPath<V, E> {
    private final GraphPath<V, E> graphPath;
    private final JSONSerializer jsonSerializer = new JSONSerializer().exclude("*.class");
    private final JSONDeserializer<ArrayList<V>> jsonDeserializer = new JSONDeserializer<>();

    public BuyerGraphPath(GraphPath<V, E> graphPath) {
        this.graphPath = graphPath;
    }



    @Override
    public Graph<V, E> getGraph() {
        return graphPath.getGraph();
    }

    @Override
    public V getStartVertex() {
        return graphPath.getStartVertex();
    }

    @Override
    public V getEndVertex() {
        return graphPath.getEndVertex();
    }

    @Override
    public List<E> getEdgeList() {
        return graphPath.getEdgeList();
    }

    @Override
    public double getWeight() {
        return graphPath.getWeight();
    }

    public List<V> getVertices() {
        final List<E> edgeList = getEdgeList();
        final Graph<V, E> graph = graphPath.getGraph();
        List<V> vertices = new ArrayList<>(edgeList.size() + 1);

        vertices.add(graphPath.getStartVertex());
        edgeList.forEach(weightedEdge -> vertices.add(graph.getEdgeTarget(weightedEdge)));

        return vertices;
    }

    public String serializePath() {
        return jsonSerializer.serialize(getVertices());
    }

    public static <V> List<V> deserializePath(String serializedPath) {
        return (new JSONDeserializer<ArrayList<V>>()).deserialize(serializedPath);
    }

}
