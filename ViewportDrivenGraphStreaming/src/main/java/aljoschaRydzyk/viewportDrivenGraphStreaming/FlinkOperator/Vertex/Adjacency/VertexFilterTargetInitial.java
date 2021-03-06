package aljoschaRydzyk.viewportDrivenGraphStreaming.FlinkOperator.Vertex.Adjacency;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.types.Row;

public class VertexFilterTargetInitial implements FilterFunction<Row> {
	private int numberVertices;
	
	public VertexFilterTargetInitial(int numberVertices) {
		this.numberVertices = numberVertices;
	}
	
	@Override
	public boolean filter(Row row) throws Exception {
		long sourceIdNumeric = (long) row.getField(2);
		long targetIdNumeric = (long) row.getField(8);
		System.out.println(sourceIdNumeric);
		System.out.println(targetIdNumeric);
		System.out.println(sourceIdNumeric > targetIdNumeric);
		System.out.println(targetIdNumeric < numberVertices);
		return sourceIdNumeric > targetIdNumeric && targetIdNumeric < numberVertices;
	}
	
}
