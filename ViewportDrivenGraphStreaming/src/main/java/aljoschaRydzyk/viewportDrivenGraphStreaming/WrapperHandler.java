package aljoschaRydzyk.viewportDrivenGraphStreaming;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nextbreakpoint.flinkclient.api.ApiException;
import com.nextbreakpoint.flinkclient.api.FlinkApi;
import com.nextbreakpoint.flinkclient.model.JobIdWithStatus;
import com.nextbreakpoint.flinkclient.model.JobIdWithStatus.StatusEnum;

import aljoschaRydzyk.viewportDrivenGraphStreaming.FlinkOperator.GraphObject.VertexGVD;
import aljoschaRydzyk.viewportDrivenGraphStreaming.FlinkOperator.GraphObject.WrapperGVD;
import aljoschaRydzyk.viewportDrivenGraphStreaming.FlinkOperator.GraphUtils.GraphUtil;
import com.nextbreakpoint.flinkclient.model.JobIdsWithStatusOverview;

public class WrapperHandler implements Serializable {
	
	private Map<String,Map<String,Object>> globalVertices;
	private Map<String,VertexGVD> innerVertices;
	private Map<String,VertexGVD> newVertices;
	private Map<String,WrapperGVD> edges;
	private Map<String,VertexGVD> layoutedVertices;
	private String operation;
	private int capacity;
	private Float top;
	private Float right;
	private Float bottom;
	private Float left;
	private VertexGVD secondMinDegreeVertex;
	private VertexGVD minDegreeVertex;    
    private boolean layout = true;
    private int maxVertices = 100;
    private FlinkApi api;
    private int operationStep;
    public boolean sentToClientInSubStep;
	private boolean stream;
	private Server server;

    public WrapperHandler (Server server) {
    	this.server = server;
    	System.out.println("wrapper handler constructor is executed");
    }
	 
	public void initializeAPI(String localMachinePublicIp4) {
		api = new FlinkApi();
        api.getApiClient().setBasePath("http://" + localMachinePublicIp4 + ":8081");  
	}
	
	public void initializeGraphRepresentation() {
	  	System.out.println("initializing graph representation");
	  	operation = "initial";
		globalVertices = new HashMap<String,Map<String,Object>>();
		innerVertices = new HashMap<String,VertexGVD>();
		newVertices = new HashMap<String,VertexGVD>();
		edges = new HashMap<String,WrapperGVD>();
	}
	  
	public void prepareOperation(){
		System.out.println("top, right, bottom, left:" + top + " " + right + " "+ bottom + " " + left);
		if (operation != "zoomOut"){
			for (Map.Entry<String, WrapperGVD> entry : edges.entrySet()) {
				WrapperGVD wrapper = entry.getValue();
				int sourceX;
				int sourceY;
				int targetX;
				int targetY;
				if (layout) {
					sourceX = wrapper.getSourceX();
					sourceY = wrapper.getSourceY();
					targetX = wrapper.getTargetX();
					targetY = wrapper.getTargetY();
				} else {
					VertexGVD sourceVertex = (VertexGVD) layoutedVertices.get(wrapper.getSourceVertex().getIdGradoop());
					sourceX = sourceVertex.getX();
					sourceY = sourceVertex.getY();
					VertexGVD targetVertex = (VertexGVD) layoutedVertices.get(wrapper.getTargetVertex().getIdGradoop());
					targetX = targetVertex.getX();
					targetY = targetVertex.getY();
				}
				if (((sourceX < left) || (right < sourceX) || (sourceY < top) || (bottom < sourceY)) &&
						((targetX  < left) || (right < targetX ) || (targetY  < top) || (bottom < targetY))){
					server.sendToAll("removeObjectServer;" + wrapper.getEdgeIdGradoop());
					System.out.println("Removing Object in prepareOperation, ID: " + wrapper.getEdgeIdGradoop());
				}
			}			
			System.out.println("innerVertices size before removing in prepareOperation: " + innerVertices.size());
			Iterator<Map.Entry<String,VertexGVD>> iter = innerVertices.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String,VertexGVD> entry = iter.next();
				VertexGVD vertex = entry.getValue();
				System.out.println("VertexID: " + vertex.getIdGradoop() + ", VertexX: " + vertex.getX() + ", VertexY: "+ vertex.getY());
				if ((vertex.getX() < left) || (right < vertex.getX()) || (vertex.getY() < top) || (bottom < vertex.getY())) {
					iter.remove();
					System.out.println("Removing Object in prepareOperation in innerVertices only, ID: " + vertex.getIdGradoop());
				}
			}
			System.out.println("innerVertices size after removing in prepareOPeration: " + innerVertices.size());
			
		}
		capacity = maxVertices - innerVertices.size();
		if (operation.equals("pan") || operation.equals("zoomOut")) {
			newVertices = innerVertices;
			if (capacity < 0) {
				List<VertexGVD> list = new ArrayList<VertexGVD>(newVertices.values());
				list.sort(new VertexGVDNumericIdComparator().reversed());
				System.out.println("wrapperHandler, list sorted, idNumeric: " + list.get(0).getIdNumeric());
//				for (VertexGVD vertex : list) System.out.println(vertex.getIdNumeric());
				System.out.println("wrapperHandler, list size: " + list.size());	
				while (capacity < 0) {
					list.remove(list.size() - 1);
					capacity += 1;
				}
				System.out.println("wrapperHandler, list size after rremove: " + list.size());
				newVertices = new HashMap<String,VertexGVD>();
				for (VertexGVD vertex : list) {
					newVertices.put(vertex.getIdGradoop(), vertex);
				}
			}
			for (String vId : newVertices.keySet()) System.out.println("prepareOperation, newVertices key: " + vId);
			innerVertices = new HashMap<String,VertexGVD>();
			System.out.println(newVertices.size());
			//this is necessary in case the (second)minDegreeVertex will get deleted in the clear up step befor
			if (newVertices.size() > 1) {
				updateMinDegreeVertices(newVertices);
			} else if (newVertices.size() == 1) {
				minDegreeVertex = newVertices.values().iterator().next();
			}
			System.out.println("Capacity after prepareOperation: " + capacity);
		} else {
			if (capacity < 0) {
				List<VertexGVD> list = new ArrayList<VertexGVD>(innerVertices.values());
				list.sort(new VertexGVDNumericIdComparator().reversed());
				System.out.println("wrapperHandler, list sorted, idNumeric: " + list.get(0).getIdNumeric());
//				for (VertexGVD vertex : list) System.out.println(vertex.getIdNumeric());
				System.out.println("wrapperHandler, list size: " + list.size());	
				while (capacity < 0) {
					list.remove(list.size() - 1);
					capacity += 1;
				}
				System.out.println("wrapperHandler, list size after rremove: " + list.size());
				innerVertices = new HashMap<String,VertexGVD>();
				for (VertexGVD vertex : list) {
					innerVertices.put(vertex.getIdGradoop(), vertex);
				}
			}
			//this is necessary in case the (second)minDegreeVertex will get deleted in the clear up step befor
			if (innerVertices.size() > 1) {
				updateMinDegreeVertices(innerVertices);
			} else if (innerVertices.size() == 1) {
				minDegreeVertex = innerVertices.values().iterator().next();
			}
			System.out.println("Capacity after prepareOperation: " + capacity);
			newVertices = new HashMap<String,VertexGVD>();
		}
		
	}
	
	public void addWrapperCollectionInitial(List<WrapperGVD> wrapperCollection) {
		Iterator<WrapperGVD> iter = wrapperCollection.iterator();
		while (iter.hasNext()) addWrapperInitial(iter.next());
	}
	
	public void addWrapperInitial(WrapperGVD wrapper) {
		System.out.println("SourceIdNumeric: " + wrapper.getSourceIdNumeric());
		System.out.println("SourceIdGradoop: " + wrapper.getSourceIdGradoop());
		System.out.println("TargetIdNumeric: " + wrapper.getTargetIdNumeric());
		System.out.println("TargetIdGradoop: " + wrapper.getTargetIdGradoop());
		System.out.println("WrapperLabel: " + wrapper.getEdgeLabel());
		if (wrapper.getEdgeLabel().equals("identityEdge")) {
			addWrapperIdentityInitial(wrapper.getSourceVertex());
		} else {
			addNonIdentityWrapperInitial(wrapper);
		}
	}
	
	private void addWrapperIdentityInitial(VertexGVD vertex) {
		boolean added = addVertex(vertex);
		if (added) innerVertices.put(vertex.getIdGradoop(), vertex);
	}
	
	private void addNonIdentityWrapperInitial(WrapperGVD wrapper) {
		VertexGVD sourceVertex = wrapper.getSourceVertex();
		VertexGVD targetVertex = wrapper.getTargetVertex();
		boolean addedSource = addVertex(sourceVertex);
		if (addedSource) innerVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
		boolean addedTarget = addVertex(targetVertex);
		if (addedTarget) innerVertices.put(targetVertex.getIdGradoop(), targetVertex);
		addEdge(wrapper);
	}
	
	public void addWrapperCollection(List<WrapperGVD> wrapperCollection) {
		Iterator<WrapperGVD> iter = wrapperCollection.iterator();
		while (iter.hasNext()) addWrapper(iter.next());
	}
	  
	public void addWrapper(WrapperGVD wrapper) {
		System.out.println("EdgeIdGradoop: " + wrapper.getEdgeIdGradoop());
		System.out.println("SourceIdNumeric: " + wrapper.getSourceIdNumeric());
		System.out.println("SourceIdGradoop: " + wrapper.getSourceIdGradoop());
		System.out.println("TargetIdNumeric: " + wrapper.getTargetIdNumeric());
		System.out.println("TargetIdGradoop: " + wrapper.getTargetIdGradoop());
		System.out.println("WrapperLabel: " + wrapper.getEdgeLabel());
		System.out.println("Size of innerVertices: " + innerVertices.size());
		System.out.println("Size of newVertices: " + newVertices.size());
		System.out.println("ID Gradoop minDegreeVertex, degree: " + minDegreeVertex.getIdGradoop() + " " +
				minDegreeVertex.getIdGradoop());
		System.out.println("ID Gradoop secondMinDegreeVertex, degree: " + secondMinDegreeVertex.getIdGradoop() +
				" " + secondMinDegreeVertex.getIdGradoop());
		System.out.println("Capacity: " + capacity);
		if (wrapper.getEdgeLabel().equals("identityEdge")) {
			addWrapperIdentity(wrapper.getSourceVertex());
		} else {
			addNonIdentityWrapper(wrapper);
		}
	}
	  
	private void addWrapperIdentity(VertexGVD vertex) {
		System.out.println("In addWrapperIdentity");
		String vertexId = vertex.getIdGradoop();
		boolean vertexIsRegisteredInside = newVertices.containsKey(vertexId) || innerVertices.containsKey(vertexId);
		if (capacity > 0) {
			addVertex(vertex);
			if (!vertexIsRegisteredInside) {
				newVertices.put(vertex.getIdGradoop(), vertex);
				updateMinDegreeVertex(vertex);
				capacity -= 1;
			}
		} else {
			System.out.println("In addWrapperIdentity, declined capacity < 0");
			if (vertex.getDegree() > minDegreeVertex.getDegree()) {
				addVertex(vertex);
				if (!vertexIsRegisteredInside) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					registerInside(vertex);
				}
			} else {
				System.out.println("In addWrapperIdentity, declined vertexDegree > minDegreeVertexDegree");
			}
		}
	}
	  
	private void addNonIdentityWrapper(WrapperGVD wrapper) {
		VertexGVD sourceVertex = wrapper.getSourceVertex();
		VertexGVD targetVertex = wrapper.getTargetVertex();
		String sourceId = sourceVertex.getIdGradoop();
		String targetId = targetVertex.getIdGradoop();
		boolean sourceIsRegisteredInside = newVertices.containsKey(sourceId) || innerVertices.containsKey(sourceId);
		boolean targetIsRegisteredInside = newVertices.containsKey(targetId) || innerVertices.containsKey(targetId);
		if (capacity > 1) {
			addVertex(sourceVertex);
			if ((sourceVertex.getX() >= left) && (right >= sourceVertex.getX()) && (sourceVertex.getY() >= top) && 
					(bottom >= sourceVertex.getY()) && !sourceIsRegisteredInside){
				updateMinDegreeVertex(sourceVertex);
				newVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
				capacity -= 1;
			}
			addVertex(targetVertex);
			if ((targetVertex.getX() >= left) && (right >= targetVertex.getX()) && (targetVertex.getY() >= top) 
					&& (bottom >= targetVertex.getY()) && !targetIsRegisteredInside){
				updateMinDegreeVertex(targetVertex);
				newVertices.put(targetVertex.getIdGradoop(), targetVertex);
				capacity -= 1;
			}
			addEdge(wrapper);
		} else if (capacity == 1){
			boolean sourceIn = true;
			boolean targetIn = true;
			if ((sourceVertex.getX() < left) || (right < sourceVertex.getX()) || (sourceVertex.getY() < top) || 
					(bottom < sourceVertex.getY())){
				sourceIn = false;
			}
			if ((targetVertex.getX() < left) || (right < targetVertex.getX()) || (targetVertex.getY() < top) || 
					(bottom < targetVertex.getY())){
				targetIn = false;
			}
			System.out.println("In addNonIdentityWrapper, capacity == 1, sourceID: " + sourceVertex.getIdGradoop() + ", sourceIn: " + sourceIn + 
					", targetID: " + targetVertex.getIdGradoop() + ", targetIn: " + targetIn);
			if (sourceIn && targetIn) {
				boolean sourceAdmission = false;
				boolean targetAdmission = false;
				if (sourceVertex.getDegree() > targetVertex.getDegree()) {
					addVertex(sourceVertex);
					sourceAdmission = true;
					if (targetVertex.getDegree() > minDegreeVertex.getDegree() || sourceIsRegisteredInside) {
						addVertex(targetVertex);
						targetAdmission = true;
						addEdge(wrapper);
					}
				} else {
					addVertex(targetVertex);
					targetAdmission = true;
					if (sourceVertex.getDegree() > minDegreeVertex.getDegree() || targetIsRegisteredInside) {
						addVertex(sourceVertex);
						sourceAdmission = true;
						addEdge(wrapper);
					}
				}
				if (!sourceIsRegisteredInside && sourceAdmission && !targetIsRegisteredInside && targetAdmission) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					newVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
					newVertices.put(targetVertex.getIdGradoop(), targetVertex);
					updateMinDegreeVertices(newVertices);
				} else if (!sourceIsRegisteredInside && sourceAdmission) {
					registerInside(sourceVertex);
				} else if (!targetIsRegisteredInside && targetAdmission) {
					registerInside(targetVertex);
				}
				capacity -= 1 ;
			} else if (sourceIn) {
				addVertex(sourceVertex);
				addVertex(targetVertex);
				addEdge(wrapper);
				if (!sourceIsRegisteredInside) {
					capacity -= 1 ;
					registerInside(sourceVertex);
				}
			} else if (targetIn) {
				addVertex(targetVertex);
				addVertex(sourceVertex);
				addEdge(wrapper);
				if (!targetIsRegisteredInside) {
					capacity -= 1 ;
					registerInside(targetVertex);
				}
			}
		} else {
			boolean sourceIn = true;
			boolean targetIn = true;
			if ((sourceVertex.getX() < left) || (right < sourceVertex.getX()) || (sourceVertex.getY() < top) || 
					(bottom < sourceVertex.getY())){
				sourceIn = false;
			}
			if ((targetVertex.getX() < left) || (right < targetVertex.getX()) || (targetVertex.getY() < top) || 
					(bottom < targetVertex.getY())){
				targetIn = false;
			}
			if (sourceIn && targetIn && (sourceVertex.getDegree() > secondMinDegreeVertex.getDegree()) && 
					(targetVertex.getDegree() > secondMinDegreeVertex.getDegree())) {
				addVertex(sourceVertex);
				addVertex(targetVertex);
				addEdge(wrapper);
				if (!sourceIsRegisteredInside && !targetIsRegisteredInside) {
					reduceNeighborIncidence(minDegreeVertex);
					reduceNeighborIncidence(secondMinDegreeVertex);
					removeVertex(secondMinDegreeVertex);
					removeVertex(minDegreeVertex);
					newVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
					newVertices.put(targetVertex.getIdGradoop(), targetVertex);
					updateMinDegreeVertices(newVertices);
				} else if (!sourceIsRegisteredInside) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					registerInside(sourceVertex);
				} else if (!targetIsRegisteredInside) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					registerInside(targetVertex);
				}
			} else if (sourceIn && !(targetIn) && (sourceVertex.getDegree() > minDegreeVertex.getDegree() || sourceIsRegisteredInside)) {
				addVertex(sourceVertex);
				addVertex(targetVertex);
				addEdge(wrapper);
				if (!sourceIsRegisteredInside) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					registerInside(sourceVertex);
				}
			} else if (targetIn && !(sourceIn) && (targetVertex.getDegree() > minDegreeVertex.getDegree() || targetIsRegisteredInside)) {
				addVertex(sourceVertex);
				addVertex(targetVertex);
				addEdge(wrapper);
				if (!targetIsRegisteredInside) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					registerInside(targetVertex);
				}
			} else {
				System.out.println("nonIdentityWrapper not Added!");
			}
		}											
	}
	
	public void addWrapperCollectionLayout(List<WrapperGVD> wrapperCollection) {
		Iterator<WrapperGVD> iter = wrapperCollection.iterator();
		while (iter.hasNext()) addWrapperLayout(iter.next());
	}
	
	public void addWrapperLayout(WrapperGVD wrapper) {
		//if in zoomIn3 or pan3: cancel flinkjob if still running, close socket and reopen for next step and move to next step!
		System.out.println("wrapperHandler, operationStep: " + operationStep);
		if (operationStep == 3 && (operation == "zoomIn" || operation == "pan")) {
			if (capacity == 0) {
				try {
					JobIdsWithStatusOverview jobs = api.getJobs();
					List<JobIdWithStatus> list = jobs.getJobs();
					System.out.println("flink api job list size: " + list.size());
					Iterator<JobIdWithStatus> iter = list.iterator();
					JobIdWithStatus job;
					while (iter.hasNext()) {
						job = iter.next();
						if (job.getStatus() == StatusEnum.RUNNING) {
							api.terminateJob(job.getId(), "cancel");
							break;
						}
					}
					if (stream) server.getFlinkResponseHandler().closeAndReopen();
				} catch (ApiException e) {
					e.printStackTrace();
				}
			}
		} 
		System.out.println("EdgeIdGradoop; " + wrapper.getEdgeIdGradoop());
		System.out.println("SourceIdNumeric: " + wrapper.getSourceIdNumeric());
		System.out.println("SourceIdGradoop: " + wrapper.getSourceIdGradoop());
		System.out.println("TargetIdNumeric: " + wrapper.getTargetIdNumeric());
		System.out.println("TargetIdGradoop: " + wrapper.getTargetIdGradoop());
		System.out.println("WrapperLabel: " + wrapper.getEdgeLabel());
		System.out.println("Size of innerVertices: " + innerVertices.size());
		System.out.println("Size of newVertices: " + newVertices.size());
		System.out.println("ID Gradoop minDegreeVertex, degree: " + minDegreeVertex.getIdGradoop() + " " +
				minDegreeVertex.getIdGradoop());
		System.out.println("ID Gradoop secondMinDegreeVertex, degree: " + secondMinDegreeVertex.getIdGradoop() +
				" " + secondMinDegreeVertex.getIdGradoop());
		System.out.println("Capacity: " + capacity);
		if (wrapper.getEdgeLabel().equals("identityEdge")) {
			addWrapperIdentityLayout(wrapper.getSourceVertex());
		} else {
			addNonIdentityWrapperLayout(wrapper);
		}
	}
	
	private void addWrapperIdentityLayout(VertexGVD vertex) {
		System.out.println("In addWrapperIdentityLayout");
		String vertexId = vertex.getIdGradoop();
		boolean vertexIsRegisteredInside = newVertices.containsKey(vertexId) || innerVertices.containsKey(vertexId);
		if (capacity > 0) {
			addVertex(vertex);
			if (!vertexIsRegisteredInside) {
				newVertices.put(vertex.getIdGradoop(), vertex);
				updateMinDegreeVertex(vertex);
				capacity -= 1;
			}
		} else {
			System.out.println("In addWrapperIdentityLayout, declined capacity > 0");
			if (vertex.getDegree() > minDegreeVertex.getDegree()) {
				addVertex(vertex);
				if (!vertexIsRegisteredInside) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					registerInside(vertex);
				}
			} else {
				System.out.println("In addWrapperIdentity, declined vertexDegree > minDegreeVertexDegree");
				System.out.println("identityWrapper not Added!");
			}
		}
	}
	
	private void addNonIdentityWrapperLayout(WrapperGVD wrapper) {
		VertexGVD sourceVertex = wrapper.getSourceVertex();
		VertexGVD targetVertex = wrapper.getTargetVertex();
		String sourceId = sourceVertex.getIdGradoop();
		String targetId = targetVertex.getIdGradoop();
		VertexGVD sourceLayouted = null;
		VertexGVD targetLayouted = null;
		boolean sourceIsRegisteredInside = newVertices.containsKey(sourceId) || innerVertices.containsKey(sourceId);
		boolean targetIsRegisteredInside = newVertices.containsKey(targetId) || innerVertices.containsKey(targetId);
		if (layoutedVertices.containsKey(sourceVertex.getIdGradoop())) sourceLayouted = layoutedVertices.get(sourceVertex.getIdGradoop());
		if (layoutedVertices.containsKey(targetVertex.getIdGradoop())) targetLayouted = layoutedVertices.get(targetVertex.getIdGradoop());
		
		//Both Nodes have coordinates and can be treated as usual
		if (sourceLayouted != null && targetLayouted != null) {
			sourceVertex.setX(sourceLayouted.getX());
			sourceVertex.setY(sourceLayouted.getY());
			targetVertex.setX(targetLayouted.getX());
			targetVertex.setY(targetLayouted.getY());
			addNonIdentityWrapper(wrapper);
		}
		
		//Only one node has coordinates, then this node is necessarily already visualized and the other node necessarily needs to be layouted inside
		else if (sourceLayouted != null) {
			if (capacity > 0) {
				addVertex(targetVertex);
				if (!targetIsRegisteredInside) {
					updateMinDegreeVertex(targetVertex);
					newVertices.put(targetVertex.getIdGradoop(), targetVertex);
					capacity -= 1;
				}
				addEdge(wrapper);
			} else {
				if (targetVertex.getDegree() > minDegreeVertex.getDegree() || targetIsRegisteredInside) {
					addVertex(targetVertex);
					addEdge(wrapper);
					if (!targetIsRegisteredInside) {
						reduceNeighborIncidence(minDegreeVertex);
						removeVertex(minDegreeVertex);
						registerInside(targetVertex);
					}
				} else {
					System.out.println("nonIdentityWrapper not Added!");
				}
			}
		} else if (targetLayouted != null) {
			if (capacity > 0) {
				addVertex(sourceVertex);
				if (!sourceIsRegisteredInside) {
					updateMinDegreeVertex(sourceVertex);
					newVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
					capacity -= 1;
				}
				addEdge(wrapper);
			} else {
				if (sourceVertex.getDegree() > minDegreeVertex.getDegree() || sourceIsRegisteredInside) {
					addVertex(sourceVertex);
					addEdge(wrapper);
					if (!sourceIsRegisteredInside) {
						reduceNeighborIncidence(minDegreeVertex);
						removeVertex(minDegreeVertex);
						registerInside(sourceVertex);
					}
				} else {
					System.out.println("nonIdentityWrapper not Added!");
				}
			}
		}
		
		//Both nodes do not have coordinates. Then both nodes necessarily need to be layouted inside
		else {
			if (capacity > 1) {
				addVertex(sourceVertex);
				if (!sourceIsRegisteredInside){
					updateMinDegreeVertex(sourceVertex);
					newVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
					capacity -= 1;
				}
				addVertex(targetVertex);
				if (!targetIsRegisteredInside){
					updateMinDegreeVertex(targetVertex);
					newVertices.put(targetVertex.getIdGradoop(), targetVertex);
					capacity -= 1;
				}
				addEdge(wrapper);
			} else if (capacity == 1) {
				boolean sourceAdmission = false;
				boolean targetAdmission = false;
				if (sourceVertex.getDegree() > targetVertex.getDegree()) {
					addVertex(sourceVertex);
					sourceAdmission = true;
					if (targetVertex.getDegree() > minDegreeVertex.getDegree() || sourceIsRegisteredInside) {
						addVertex(targetVertex);
						targetAdmission = true;
						addEdge(wrapper);
					}
				} else {
					addVertex(targetVertex);
					targetAdmission = true;
					if (sourceVertex.getDegree() > minDegreeVertex.getDegree() || targetIsRegisteredInside) {
						addVertex(sourceVertex);
						sourceAdmission = true;
						addEdge(wrapper);
					}
				}
				if (!sourceIsRegisteredInside && sourceAdmission && !targetIsRegisteredInside && targetAdmission) {
					reduceNeighborIncidence(minDegreeVertex);
					removeVertex(minDegreeVertex);
					newVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
					newVertices.put(targetVertex.getIdGradoop(), targetVertex);
					updateMinDegreeVertices(newVertices);
				} else if (!sourceIsRegisteredInside && sourceAdmission) {
					registerInside(sourceVertex);
				} else if (!targetIsRegisteredInside && targetAdmission) {
					registerInside(targetVertex);
				}
				capacity -= 1 ;
			} else {
				if ((sourceVertex.getDegree() > secondMinDegreeVertex.getDegree()) && 
						(targetVertex.getDegree() > secondMinDegreeVertex.getDegree())) {
					addVertex(sourceVertex);
					addVertex(targetVertex);
					addEdge(wrapper);
					if (!sourceIsRegisteredInside && !targetIsRegisteredInside) {
						reduceNeighborIncidence(minDegreeVertex);
						reduceNeighborIncidence(secondMinDegreeVertex);
						removeVertex(secondMinDegreeVertex);
						removeVertex(minDegreeVertex);
						newVertices.put(sourceVertex.getIdGradoop(), sourceVertex);
						newVertices.put(targetVertex.getIdGradoop(), targetVertex);
						updateMinDegreeVertices(newVertices);
					} else if (!sourceIsRegisteredInside) {
						reduceNeighborIncidence(minDegreeVertex);
						removeVertex(minDegreeVertex);
						registerInside(sourceVertex);
					} else if (!targetIsRegisteredInside) {
						reduceNeighborIncidence(minDegreeVertex);
						removeVertex(minDegreeVertex);
						registerInside(targetVertex);
					}
				} else if (sourceVertex.getDegree() > minDegreeVertex.getDegree() || sourceIsRegisteredInside) {
					addVertex(sourceVertex);
					if (!sourceIsRegisteredInside) {
						reduceNeighborIncidence(minDegreeVertex);
						removeVertex(minDegreeVertex);
						registerInside(sourceVertex);
					}
				} else if (targetVertex.getDegree() > minDegreeVertex.getDegree() || targetIsRegisteredInside) {
					addVertex(targetVertex);
					if (!targetIsRegisteredInside) {
						reduceNeighborIncidence(minDegreeVertex);
						removeVertex(minDegreeVertex);
						registerInside(targetVertex);
					}
				} else {
					System.out.println("nonIdentityWrapper not Added!");
				}
			}
		}
	}
	  
	public void removeWrapper(WrapperGVD wrapper) {
		if (wrapper.getEdgeIdGradoop() != "identityEdge") {
			String targetId = wrapper.getTargetIdGradoop();
			int targetIncidence = (int) globalVertices.get(targetId).get("incidence");
			if (targetIncidence == 1) {
				globalVertices.remove(targetId);
				if (innerVertices.containsKey(targetId)) innerVertices.remove(targetId);
				if (newVertices.containsKey(targetId)) newVertices.remove(targetId);
				System.out.println("removing object in removeWrapper (target), ID: " + wrapper.getTargetIdGradoop());
				server.sendToAll("removeObjectServer;" + wrapper.getTargetIdGradoop());
			} else {
				globalVertices.get(targetId).put("incidence", targetIncidence - 1);
			}
		}
		String sourceId = wrapper.getSourceIdGradoop();
		System.out.println("globalVertices, size: " + globalVertices.size());
		System.out.println(sourceId);
		System.out.println(globalVertices.get(sourceId));
		int sourceIncidence = (int) globalVertices.get(sourceId).get("incidence");
		if (sourceIncidence == 1) {
			globalVertices.remove(sourceId);
			if (innerVertices.containsKey(sourceId)) innerVertices.remove(sourceId);
			if (newVertices.containsKey(sourceId)) newVertices.remove(sourceId);
			server.sendToAll("removeObjectServer;" + wrapper.getSourceIdGradoop());
			System.out.println("removing object in removeWrapper (source), ID: " + wrapper.getSourceIdGradoop());
		} else {
			globalVertices.get(sourceId).put("incidence", sourceIncidence - 1);
		}
		edges.remove(wrapper.getEdgeIdGradoop());
	}
	  
	private boolean addVertex(VertexGVD vertex) {
		System.out.println("In addVertex");
		String sourceId = vertex.getIdGradoop();
		if (!(globalVertices.containsKey(sourceId))) {
			Map<String,Object> map = new HashMap<String,Object>();
			map.put("incidence", (int) 1);
			map.put("vertex", vertex);
			globalVertices.put(sourceId, map);
			System.out.println("addVertex, globalVertices, incidence: " + 
					globalVertices.get(sourceId).get("incidence"));
			if (layout) {
				System.out.println("channel size of Server: " + server.channels.size());
				server.sendToAll("addVertexServer;" + vertex.getIdGradoop() + ";" + vertex.getX() + ";" + 
				vertex.getY() + ";" + vertex.getIdNumeric() + ";" + vertex.getDegree());
				sentToClientInSubStep = true;
			} else {
				if (layoutedVertices.containsKey(vertex.getIdGradoop())) {
					VertexGVD layoutedVertex = layoutedVertices.get(vertex.getIdGradoop());
					server.sendToAll("addVertexServer;" + vertex.getIdGradoop() + ";" + layoutedVertex.getX() + ";" + layoutedVertex.getY() + ";" 
							+ vertex.getIdNumeric() + ";" + vertex.getDegree());
					sentToClientInSubStep = true;
				} else {
					server.sendToAll("addVertexServerToBeLayouted;" + vertex.getIdGradoop() + ";" + vertex.getDegree() + ";" + vertex.getIdNumeric());
					sentToClientInSubStep = true;
				}
			}
			return true;
		} else {
			System.out.println("In addVertex, declined because ID contained in globalVertices");
			Map<String,Object> map = globalVertices.get(sourceId);
			map.put("incidence", (int) map.get("incidence") + 1);
			return false;
		}		
	}
	
	private void removeVertex(VertexGVD vertex) {	
		if (!globalVertices.containsKey(vertex.getIdGradoop())) {
			System.out.println("cannot remove vertex because not in vertexGlobalMap, id: " + vertex.getIdGradoop());
		} else {
			newVertices.remove(vertex.getIdGradoop());
			globalVertices.remove(vertex.getIdGradoop());
			server.sendToAll("removeObjectServer;" + vertex.getIdGradoop());
			Iterator<WrapperGVD> iter = edges.values().iterator();
			while (iter.hasNext()) {
				WrapperGVD wrapper = iter.next();
				String sourceId = wrapper.getSourceIdGradoop();
				String targetId = wrapper.getTargetIdGradoop();
				String vertexId = vertex.getIdGradoop();
				if (sourceId.equals(vertexId) || targetId.equals(vertexId)) iter.remove();
			}
			System.out.println("Removing Obect in removeVertex, ID: " + vertex.getIdGradoop());
		}
	}
	  
	private void addEdge(WrapperGVD wrapper) {
		edges.put(wrapper.getEdgeIdGradoop(), wrapper);
		server.sendToAll("addEdgeServer;" + wrapper.getEdgeIdGradoop() + ";" + wrapper.getSourceIdGradoop() + ";" + wrapper.getTargetIdGradoop());
		sentToClientInSubStep = true;
	}
	
	private void updateMinDegreeVertex(VertexGVD vertex) {
		//CHANGE: < to <=
		if (vertex.getDegree() <= minDegreeVertex.getDegree()) {
			secondMinDegreeVertex = minDegreeVertex;
			minDegreeVertex = vertex;
		} else if (vertex.getDegree() <= secondMinDegreeVertex.getDegree()) {
			secondMinDegreeVertex = vertex;
		}		
	}

	private void updateMinDegreeVertices(Map<String, VertexGVD> map) {
		System.out.println("in updateMinDegreeVertices");
		Collection<VertexGVD> collection = map.values();
		Iterator<VertexGVD> iter = collection.iterator();
		minDegreeVertex = iter.next();
		secondMinDegreeVertex = iter.next();
		System.out.println("Initial min degree vertices: " + minDegreeVertex.getIdGradoop()+ " " + secondMinDegreeVertex.getIdGradoop());
		if (secondMinDegreeVertex.getDegree() < minDegreeVertex.getDegree()) {
			VertexGVD temp = minDegreeVertex;
			minDegreeVertex = secondMinDegreeVertex;
			secondMinDegreeVertex = temp;
		}
		for (Map.Entry<String, VertexGVD> entry : map.entrySet()) {
			VertexGVD vertex = entry.getValue();
			if (vertex.getDegree() < minDegreeVertex.getDegree() && !vertex.getIdGradoop().equals(secondMinDegreeVertex.getIdGradoop())) {
				secondMinDegreeVertex = minDegreeVertex;
				minDegreeVertex = vertex;
			} else if (vertex.getDegree() < secondMinDegreeVertex.getDegree() && !vertex.getIdGradoop().equals(minDegreeVertex.getIdGradoop()))  {
				secondMinDegreeVertex = vertex;
			}
		}
		System.out.println("Final min degree vertices: " + minDegreeVertex.getIdGradoop() + " " + secondMinDegreeVertex.getIdGradoop());
	}
	
	private void registerInside(VertexGVD vertex) {
		newVertices.put(vertex.getIdGradoop(), vertex);
		if (newVertices.size() > 1) {
			updateMinDegreeVertices(newVertices);
		} else {
			minDegreeVertex = vertex;
		}
	}
	
	private void reduceNeighborIncidence(VertexGVD vertex) {
//		Set<String> neighborIds = getNeighborhood(vertex);
//		for (String neighbor : neighborIds) {
//			if (globalVertices.containsKey(neighbor)) {
//				Map<String,Object> map = globalVertices.get(neighbor);
//				map.put("incidence", (int) map.get("incidence") - 1); 
//			}
//		}
	}
	
	private boolean hasVisualizedNeighborsInside(VertexGVD vertex) {
		for (WrapperGVD wrapper : edges.values()) {
			String sourceId = wrapper.getSourceIdGradoop();
			String targetId = wrapper.getTargetIdGradoop();
			String vertexId = vertex.getIdGradoop();
			if (sourceId.equals(vertexId)) {
				if (innerVertices.containsKey(targetId)) return true;
			} else if (targetId.equals(vertexId)) {
				if (innerVertices.containsKey(sourceId)) return true;
			}
		}
		return false;
	}
	
	public void clearOperation(){
		System.out.println("in clear operation");
		System.out.println(operation);
		if (operation != "initial"){
			for (Map.Entry<String, VertexGVD> entry : innerVertices.entrySet()) System.out.println("innerVertex " + entry.getValue().getIdGradoop());
			if (!layout) {
				for (VertexGVD vertex : newVertices.values()) {
					VertexGVD layoutedVertex = layoutedVertices.get(vertex.getIdGradoop());
					vertex.setX(layoutedVertex.getX());
					vertex.setY(layoutedVertex.getY());
				}
			}
			System.out.println("innerVertices size before put all: " + innerVertices.size());
			innerVertices.putAll(newVertices); 
			System.out.println("innerVertices size after put all: " + innerVertices.size());
			for (Map.Entry<String, VertexGVD> entry : innerVertices.entrySet()) System.out.println("innerVertex after putAll" + entry.getValue().getIdGradoop() + " " 
					+ entry.getValue().getX());
			System.out.println("global...");
			Iterator<Map.Entry<String, Map<String,Object>>> iter = globalVertices.entrySet().iterator();
			while (iter.hasNext()) {
				VertexGVD vertex = (VertexGVD) iter.next().getValue().get("vertex");
				String vertexId = vertex.getIdGradoop();
				if ((((vertex.getX() < left) || (right < vertex.getX()) || (vertex.getY() < top) || 
						(bottom < vertex.getY())) && !hasVisualizedNeighborsInside(vertex)) ||
							((vertex.getX() >= left) && (right >= vertex.getX()) && (vertex.getY() >= top) && (bottom >= vertex.getY()) 
									&& !innerVertices.containsKey(vertexId))) {
					System.out.println("removing in clear operation " + vertexId);
					server.sendToAll("removeObjectServer;" + vertexId);
					Iterator<WrapperGVD> edgesIterator = edges.values().iterator();
					while (edgesIterator.hasNext()) {
						WrapperGVD wrapper = edgesIterator.next();
						String sourceId = wrapper.getSourceIdGradoop();
						String targetId = wrapper.getTargetIdGradoop();
						if (sourceId.equals(vertexId) || targetId.equals(vertexId)) edgesIterator.remove();
					}
					iter.remove();
				} 
			}
			//this is necessary in case the (second)minDegreeVertex will get deleted in the clear up step before (e.g. in ZoomOut)
			if (newVertices.size() > 1) {
				updateMinDegreeVertices(newVertices);
			} else if (newVertices.size() == 1) {
				minDegreeVertex = newVertices.values().iterator().next();
			}
		} else {
			if (!layout) {
				for (VertexGVD vertex : innerVertices.values()) {
					VertexGVD layoutedVertex = layoutedVertices.get(vertex.getIdGradoop());
					System.out.println(layoutedVertex.getIdGradoop() + layoutedVertex.getX());
					vertex.setX(layoutedVertex.getX());
					vertex.setY(layoutedVertex.getY());
				}
			}
			newVertices = innerVertices;
			if (newVertices.size() > 1) {
				updateMinDegreeVertices(newVertices);
			} else if (newVertices.size() == 1) {
				minDegreeVertex = newVertices.values().iterator().next();
			}
		}
		Set<String> visualizedVertices = new HashSet<String>();
		for (Map.Entry<String, VertexGVD> entry : innerVertices.entrySet()) visualizedVertices.add(entry.getKey());
		Set<String> visualizedWrappers = new HashSet<String>();
		for (Map.Entry<String, WrapperGVD> entry : edges.entrySet()) visualizedWrappers.add(entry.getKey());
		GraphUtil graphUtil =  server.getFlinkCore().getGraphUtil();
		graphUtil.setVisualizedVertices(visualizedVertices);
		graphUtil.setVisualizedWrappers(visualizedWrappers);
		for (String key : visualizedVertices) System.out.println("clearoperation, visualizedVertices: " + key);
		for (String key : visualizedWrappers) System.out.println("clearoperation, visualizedWrappers: " + key);
		System.out.println("global size "+ globalVertices.size());
	}

	public void setModelPositions(Float topModel, Float rightModel, Float bottomModel, Float leftModel) {
		this.top = topModel;
		this.right = rightModel;
		this.bottom = bottomModel;
		this.left = leftModel;
	}

	public void setOperation(String operation) {
		this.operation = operation;
	}
	
	public void setMaxVertices() {
		this.maxVertices = server.getMaxVertices();
	}

	public void setLayoutMode(boolean layoutMode) {
		System.out.println("Setting wrapperHandler layout Mode to " + layoutMode);
		this.layout = layoutMode;
	}

	public void resetLayoutedVertices() {
		this.layoutedVertices = new HashMap<String,VertexGVD>();
	}
	
	public void setOperationStep(int operationStep) {
		this.operationStep = operationStep;
	}

	public Map<String, VertexGVD> getLayoutedVertices() {
		return this.layoutedVertices;
	}

	public Map<String, VertexGVD> getNewVertices() {
		return this.newVertices;
	}
	
	public void setSentToClientInSubStep(Boolean sent) {
		this.sentToClientInSubStep = sent;
	}
	
	public Boolean getSentToClientInSubStep() {
		return this.sentToClientInSubStep;
	}

	public Map<String, VertexGVD> getInnerVertices() {
		return this.innerVertices;
	}

	public void updateLayoutedVertices(List<String> list) {
    	for (String vertexData : list) {
    		String[] arrVertexData = vertexData.split(",");
    		String vertexId = arrVertexData[0];
    		int x = Math.round(Float.parseFloat(arrVertexData[1]));
    		int y = Math.round(Float.parseFloat(arrVertexData[2]));
    		System.out.println("wrapperHandler, updateLayoutedVertices: " + vertexId + " " + x + " " + y);
			VertexGVD vertex = new VertexGVD(vertexId, x, y);
			if (layoutedVertices.containsKey(vertexId)) System.out.println("vertex already in layoutedVertices!!!");
			layoutedVertices.put(vertexId, vertex);
    	}
    	System.out.println("layoutedVertices size: ");
    	System.out.println(layoutedVertices.size());
	}

	public int getCapacity() {
		return this.capacity;
	}

	public void setStreamBool(Boolean stream) {
		this.stream = stream;
	}
	
	public Map<String,Map<String,Object>> getGlobalVertices(){
		return this.globalVertices;
	}
}
