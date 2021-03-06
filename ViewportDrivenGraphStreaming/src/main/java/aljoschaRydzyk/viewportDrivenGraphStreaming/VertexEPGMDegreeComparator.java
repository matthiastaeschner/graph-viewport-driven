package aljoschaRydzyk.viewportDrivenGraphStreaming;

import java.util.Comparator;

import org.gradoop.common.model.impl.pojo.EPGMVertex;

public class VertexEPGMDegreeComparator implements Comparator<EPGMVertex>{
	@Override
	public int compare(EPGMVertex v1, EPGMVertex v2) {
		if (v1.getPropertyValue("degree").getLong() > v2.getPropertyValue("degree").getLong()) return -1;
		else if (v1.getPropertyValue("degree").getLong() == v2.getPropertyValue("degree").getLong()) return 0;
		else return 1;
	}	
}
