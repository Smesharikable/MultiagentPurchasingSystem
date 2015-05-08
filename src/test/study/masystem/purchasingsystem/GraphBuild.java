package study.masystem.purchasingsystem;


import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.layout.mxPartitionLayout;
import com.mxgraph.layout.mxStackLayout;
import com.mxgraph.layout.orthogonal.mxOrthogonalLayout;
import com.mxgraph.model.mxICell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.graph.builder.UndirectedWeightedGraphBuilderBase;
import org.json.JSONArray;
import org.json.JSONObject;
import study.masystems.purchasingsystem.jgrapht.WeightedEdge;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class GraphBuild {
    public static void main(String[] args) {
        String filename = String.join(File.separator, ".", "src", "test", "configuration", "CityGraph.json");
        Scanner fileScanner = null;
        try {
            fileScanner = new Scanner(new File(filename)).useDelimiter("\\Z");
        } catch (FileNotFoundException e) {
            System.err.print("File not found.");
            return;
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
        JGraphXAdapter<Integer, WeightedEdge> jGraphXAdapter = new JGraphXAdapter<>(simpleWeightedGraph);
        mxGraphComponent graphComponent = new mxGraphComponent(jGraphXAdapter);

//        hierarchicalLayout(jGraphXAdapter, graphComponent);
//        partitionLayout(jGraphXAdapter, graphComponent);
//        orthogonalLayout(jGraphXAdapter, graphComponent);
//        stackLayout(jGraphXAdapter, graphComponent);
        organicLayout(jGraphXAdapter, graphComponent);

        FloydWarshallShortestPaths<Integer, WeightedEdge> floydWarshallShortestPaths
                = new FloydWarshallShortestPaths<>(simpleWeightedGraph);
        final GraphPath<Integer, WeightedEdge> shortestPath = floydWarshallShortestPaths.getShortestPath(7, 19);

        final mxGraph graph = graphComponent.getGraph();
        final HashMap<WeightedEdge, mxICell> edgeToCellMap = jGraphXAdapter.getEdgeToCellMap();
        final java.util.List<WeightedEdge> edgeList = shortestPath.getEdgeList();
        mxICell[] cells = new mxICell[edgeList.size()];
        for (int i = 0; i < edgeList.size(); i++) {
            cells[i] = edgeToCellMap.get(edgeList.get(i));
        }
        graph.setCellStyles(mxConstants.STYLE_STROKECOLOR, "green", cells);
        graphComponent.refresh();
    }
    
    private static JFrame createFrame() {
        JFrame jFrame = new JFrame();
        jFrame.setTitle("JGraphT Adapter to JGraph Demo");
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.pack();
        jFrame.setSize(400,300);
        return jFrame;
    }

    public static void organicLayout(mxGraph graph, mxGraphComponent graphComponent) {
        JFrame jFrame = createFrame();
        jFrame.getContentPane().add(graphComponent, BorderLayout.CENTER);
        final Rectangle bounds = jFrame.getBounds();
        mxOrganicLayout organicLayout = new mxOrganicLayout(graph, new Rectangle2D.Double(100, 100, 100, 100));
        organicLayout.execute(graph.getDefaultParent());
        jFrame.setVisible(true);
    }

    public static void stackLayout(mxGraph graph, mxGraphComponent graphComponent) {
        JFrame jFrame = createFrame();
        jFrame.getContentPane().add(graphComponent);
        mxStackLayout stackLayout = new mxStackLayout(graph);
        stackLayout.execute(graph.getDefaultParent());
        jFrame.setVisible(true);
    }

    public static void orthogonalLayout(mxGraph graph, mxGraphComponent graphComponent) {
        JFrame jFrame = createFrame();
        jFrame.getContentPane().add(graphComponent);
        mxOrthogonalLayout orthogonalLayout = new mxOrthogonalLayout(graph);
        orthogonalLayout.execute(graph.getDefaultParent());
        jFrame.setVisible(true);
    }

    public static void partitionLayout(mxGraph graph, mxGraphComponent graphComponent) {
        JFrame jFrame = createFrame();
        jFrame.getContentPane().add(graphComponent);
        mxPartitionLayout partitionLayout = new mxPartitionLayout(graph, true, 20, 20);
        partitionLayout.execute(graph.getDefaultParent());
        jFrame.setVisible(true);
    }

    public static void hierarchicalLayout(mxGraph graph, mxGraphComponent graphComponent) {
        JFrame jFrame = createFrame();
        jFrame.getContentPane().add(graphComponent);
        mxHierarchicalLayout hierarchicalLayout = new mxHierarchicalLayout(graph);
        hierarchicalLayout.execute(graph.getDefaultParent());
        jFrame.setVisible(true);
    }

}
