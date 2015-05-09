package study.masystems.purchasingsystem;

import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.graph.builder.UndirectedWeightedGraphBuilderBase;
import org.json.JSONArray;
import org.json.JSONObject;
import study.masystems.purchasingsystem.jgrapht.WeightedEdge;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class CityGraphBuilder {
    public SimpleWeightedGraph<Integer, WeightedEdge> read() {
        String filename = String.join(File.separator, ".", "src", "test", "configuration", "CityGraph.json");
        Scanner fileScanner = null;
        try {
            fileScanner = new Scanner(new File(filename)).useDelimiter("\\Z");
        } catch (FileNotFoundException e) {
            System.err.print("File not found.");
            return null;
        }
        String graphData = fileScanner.next();
        fileScanner.close();

        JSONObject graphJSON = new JSONObject(graphData);
        final int vertices_count = graphJSON.getInt("vertices_count");

        final UndirectedWeightedGraphBuilderBase<
                Integer,
                WeightedEdge,
                ? extends SimpleWeightedGraph<Integer,
                        WeightedEdge>,
                ?> builder = SimpleWeightedGraph.builder(WeightedEdge.class);

        for (int i = 0; i < vertices_count; i++) {
            builder.addVertex(i + 1);
        }

        final JSONArray edges = graphJSON.getJSONArray("edges");
        for (int i = 0; i < edges.length(); i++) {
            final JSONObject edge = edges.getJSONObject(i);
            final JSONArray vertices = edge.getJSONArray("vertices");
            final int sourceVertex = vertices.getInt(0);
            final int targetVertex = vertices.getInt(1);
            final int weight = edge.getInt("weight");
            builder.addEdge(sourceVertex, targetVertex, weight);
        }

        SimpleWeightedGraph<Integer, WeightedEdge> simpleWeightedGraph = builder.build();


        return simpleWeightedGraph;
    }
}
