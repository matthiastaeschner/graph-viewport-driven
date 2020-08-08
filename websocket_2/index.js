var ws = new WebSocket("ws://localhost:8887/graphData");

var handler;

ws.onopen = function() {
    console.log("Opened!");
    ws.send("Hello Server");
};

class JoinHandler{
	vertexIncidenceMap = new Map();
	edgeSet = new Set();
	
	constructor(cy){
	}
	
	resetMap(){
		this.vertexIncidenceMap = new Map();
	}
	
	addVertex(dataArray){
		var vertexId = dataArray[1];
		var vertexX = dataArray[2];
		var vertexY = dataArray[3];
		if (!this.vertexIncidenceMap.has(vertexId)){
			this.vertexIncidenceMap.set(vertexId, 1);
		} else {
			this.vertexIncidenceMap.set(vertexId, this.vertexIncidenceMap.get(vertexId) + 1);
		}
		if (this.vertexIncidenceMap.get(vertexId) == 1) {
			cy.add({group : 'nodes', data: {id: vertexId}, position: {x: parseInt(vertexX) , y: parseInt(vertexY)}});
		}
	}
	
	removeVertex(dataArray){
		var vertexId = dataArray[1];
		if (!this.vertexIncidenceMap.has(vertexId)) {
				alert("cannot remove vertex because not in vertexIncidenceMap");
		} else {
			this.vertexIncidenceMap.set(vertexId, this.vertexIncidenceMap.get(vertexId) - 1);
			if (this.vertexIncidenceMap.get(vertexId) == 0) {
				this.vertexIncidenceMap.delete(vertexId);
				cy.remove(cy.$id(vertexId));
			}
		}
	}
	
	addEdge(dataArray){
		var edgeId = dataArray[1];
		var sourceVertex = dataArray[2];
		var targetVertex = dataArray[3];
		if (!this.edgeSet.has(edgeId)) cy.add({group : 'edges', data: {id: edgeId, source: sourceVertex , target: targetVertex}});
		this.edgeSet.add(edgeId);
	}
	
	removeSpatialSelection(top, right, bottom, left){
		var map = this.vertexIncidenceMap;
		var set = this.edgeSet;
		cy.edges().forEach(
			function (edge){
				var sourceId = edge.data('source');
				var targetId = edge.data('target');
				var sourcePos = cy.getElementById(sourceId).position();
				var targetPos = cy.getElementById(targetId).position();
				if ((sourcePos.x > right) || (sourcePos.x < left) || (sourcePos.y > bottom) || (sourcePos.y < top) || 
					(targetPos.x > right) || (targetPos.x < left) || (targetPos.y > bottom) || (targetPos.y < top)){
					cy.remove(edge);
					set.delete(edge.data('id'));
				}
			}
		)
		cy.nodes().forEach(
			function (node){
			var pos = node.position();
				if ((pos.x > right) || (pos.x < left) || (pos.y > bottom) || (pos.y < top)) {
					cy.remove(node);
					map.delete(node.data('id'));
				}
			}
		)
	}	
}

class MapHandler{
	vertexDegreeMap = new Map();
	edgePotentialSource;
	edgePotentialTarget;
	
	constructor(vertexCountMax){
		this.vertexCountMax = vertexCountMax;
	}
	
	resetMap(){
		this.vertexDegreeMap = new Map();
	}
	
	addVertexHelper(vertexId, vertexX, vertexY, vertexDegree){
		var edgePotential = false;
		if (this.vertexDegreeMap.has(vertexId)) {
			edgePotential = true;
		} else if (this.vertexDegreeMap.size < this.vertexCountMax){
			cy.add({group : 'nodes', data: {id: vertexId}, position: {x: parseInt(vertexX) , y: parseInt(vertexY)}});
			this.vertexDegreeMap.set(vertexId, vertexDegree);
			edgePotential = true;
		} else {
			var removalCandidateKey = -1;
			var removalCandidateDegree =  Infinity;
			for (const [key, value] of this.vertexDegreeMap.entries()){
				if ((parseInt(value) < removalCandidateDegree) || (parseInt(value) == removalCandidateDegree && key > removalCandidateKey)){
					removalCandidateKey = key;
					removalCandidateDegree = value;
				}
			}
			if ((vertexDegree > removalCandidateDegree) || (vertexDegree == removalCandidateDegree && vertexId < removalCandidateKey)){
				cy.remove(cy.$id(removalCandidateKey));
				cy.add({group : 'nodes', data: {id: vertexId}, position: {x: parseInt(vertexX) , y: parseInt(vertexY)}});
				this.vertexDegreeMap.delete(removalCandidateKey);
				this.vertexDegreeMap.set(vertexId, vertexDegree);
				edgePotential = true;
			}
		}
		return edgePotential;
	}
		
	addWrapper(dataArray){
		this.edgePotentialSource = false;
		this.edgePotentialTarget = false;
		var sourceVertexId = dataArray[1];
		var sourceVertexX = dataArray[2];
		var sourceVertexY = dataArray[3];
		var sourceVertexDegree = parseInt(dataArray[4]);
		var targetVertexId = dataArray[5];
		var targetVertexX = dataArray[6];
		var targetVertexY = dataArray[7];
		var targetVertexDegree = parseInt(dataArray[8]);
		var edgeIdGradoop = dataArray[9];
		this.edgePotentialSource = this.addVertexHelper(sourceVertexId, sourceVertexX, sourceVertexY, sourceVertexDegree);
		this.edgePotentialTarget = this.addVertexHelper(targetVertexId, targetVertexX, targetVertexY, targetVertexDegree);
		if (this.edgePotentialSource && this.edgePotentialTarget){
			cy.add({group : 'edges', data: {id: edgeIdGradoop, source: sourceVertexId , target: targetVertexId}});
		}
	}

}

ws.onmessage = function (evt) {
	// console.log(evt.data);
	var dataArray = evt.data.split(";");
	switch (dataArray[0]){
		case 'clearGraph':
			console.log('clearing graph');
			cy.elements().remove();
			break;
		case 'layout':
			var layout = cy.layout({name: 'fcose', ready: () => {console.log("Layout ready")}, stop: () => {console.log("Layout stopped")}});
			layout.run();
			break;
		case 'fitGraph':
			console.log('fitting graph');
			cy.fit();
			cy.zoom(0.25);
			cy.pan({x:0, y:0});
			break;
		case 'positioning':
			console.log('position viewport!');
			cy.zoom(parseFloat(dataArray[1]));
			cy.pan({x:parseInt(dataArray[2]), y:parseInt(dataArray[3])});
			break;
		case 'pan':
			console.log('panning');
			cy.pan({x:parseInt(dataArray[1]), y:parseInt(dataArray[2])});
			break;
		case 'addVertex':
			handler.addVertex(dataArray);
			break;
		case 'removeVertex':
			handler.removeVertex(dataArray);
			break;
		case 'addEdge':
			handler.addEdge(dataArray);
			break;
		case 'addWrapper':
			handler.addWrapper(dataArray);
			break;
	}
};

ws.onclose = function() {
    console.log("Closed!");
};

ws.onerror = function(err) {
    console.log("Error: " + err);
};

function sendSignalRetract(){
	handler = new JoinHandler();
	ws.send("buildTopView;retract");
}

function sendSignalAppendJoin(){
	handler = new JoinHandler(cy);
	ws.send("buildTopView;appendJoin");
}

function sendSignalAppendMap(){
	handler = new MapHandler(50);
	ws.send("buildTopView;appendMap");
}

function zoomIn(){
	//remove all elements for the time being
	// removeAll();
	//hard-coded example
	var previousTop = 0;
	var previousRight = 4000;
	var previousBottom = 4000;
	var previousLeft = 0;
	var top = 0;
	var right = 2000;
	var bottom = 2000;
	var left = 0;
	ws.send("zoomIn;" + previousTop.toString() + ";" + previousRight.toString() + ";" + previousBottom.toString() + ";" + previousLeft.toString() + ";" + top.toString() + ";" + 
		right.toString() + ";" + bottom.toString() + ";" + left.toString());
	handler.removeSpatialSelection(top, right, bottom, left);
}

function pan(){
	//hard-coded example
	var top = 0;
	var right = 2000;
	var bottom = 2000;
	var left = 0;
	var xDiff = 800;
	var yDiff = 0;
	ws.send("pan;" + top.toString() + ";" + right.toString() + ";" + bottom.toString() + ";" + left.toString() + ";" + xDiff.toString() + ";" + yDiff.toString());
	handler.removeSpatialSelection(top + yDiff, right + xDiff, bottom + yDiff, left + xDiff);
}

function removeSpatialSelection(top, right, bottom, left){
	cy.nodes().forEach(
		function (node){
		var pos = node.position();
			// console.log(node.position());
			// console.log(node.position().x);
			if ((pos.x > right) || (pos.x < left) || (pos.y > bottom) || (pos.y < top)) {
				cy.remove(node);
				
			}
		}
	)
}

function removeAll(){
	cy.elements().remove();
	handler.resetMap();
}

function displayAll(){
	handler = new JoinHandler();
	ws.send("displayAll");
}

var header1 = document.getElementById('header1');
header1.addEventListener("mousedown", 
	function(){
		console.log("Mouse went down on header1");
	}
);

document.getElementById('cy').addEventListener('mousedown', 
	function(){
		console.log("Mouse went down on cytoscape container");
	}
)