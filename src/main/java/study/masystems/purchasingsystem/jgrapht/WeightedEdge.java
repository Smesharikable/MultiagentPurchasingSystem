package study.masystems.purchasingsystem.jgrapht;

import org.jgrapht.graph.DefaultWeightedEdge;

public class WeightedEdge extends DefaultWeightedEdge {
    @Override
    public String toString() {
        return String.valueOf(getWeight());
    }
}
