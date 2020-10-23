package aljoschaRydzyk.Gradoop_Flink_Prototype;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.types.Row;

public class VertexFilterOuter implements FilterFunction<Row> {
	private Float topModel;
	private Float rightModel;
	private Float bottomModel;
	private Float leftModel;
	
	public VertexFilterOuter(Float topModel, Float rightModel, Float bottomModel, Float leftModel) {
		this.topModel = topModel;
		this.rightModel = rightModel;
		this.bottomModel = bottomModel;
		this.leftModel = leftModel;
	}
	@Override
	public boolean filter(Row value) throws Exception {
		Integer x = (Integer) value.getField(4);
		Integer y = (Integer) value.getField(5);
		return (leftModel > x) || (x > rightModel) || (topModel > y) || (y > bottomModel);
	}
}