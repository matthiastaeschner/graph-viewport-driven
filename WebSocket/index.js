// let ws = new WebSocket("ws://139.18.13.19:8897/graphData");
let ws;
let handler;

let messageQueue = new Array();
let messageProcessing;
let graphOperationLogic = "serverSide";
let layout = true;
let boundingBoxVar;
let boundingBoxVarOld;

function addMessageToQueue(dataArray){
		messageQueue.push(dataArray);
		if (!messageProcessing) {
			messageProcessing = true;
			processMessage(); 
		}
	}
	
async function processMessage(){
	if (messageQueue.length > 0){
		let dataArray = messageQueue.shift();
		let promise = new Promise((resolve, reject) => {
			switch (dataArray[0]){
				case 'addWrapper':
					handler.addWrapper(dataArray);
					break;
				case 'removeWrapper':
					handler.removeWrapper(dataArray);
					break;
				case 'addVertexServer':
					cy.add({group : 'nodes', data: {id: dataArray[1], label: dataArray[4]}, position: {x: parseInt(dataArray[2]) , y: parseInt(dataArray[3])}});
					if (!layout){
						clearTimeout(this.timeOut);
						this.timeOut = setTimeout(finalOperations, 2000);
					}
					break;
				case 'addVertexServerToBeLayouted':
					addVertexToLayoutBase(dataArray);
						clearTimeout(this.timeOut);
						this.timeOut = setTimeout(finalOperations, 2000);
					break;
				case 'addEdgeServer':
					cy.add({group : 'edges', data: {id: dataArray[1], source: dataArray[2], target: dataArray[3]}});
					console.log(cy.$id(dataArray[1]).style());
					if (!layout){
						clearTimeout(this.timeOut);
						this.timeOut = setTimeout(finalOperations, 2000);
					}
					break;
				case 'removeObjectServer':
					console.log(cy.$id(dataArray[1]));
					if (!layout) {
						console.log(layoutBase.length);
						console.log(layoutBase);
						layoutBase.delete(dataArray[1]);
						console.log(layoutBase.length);
					}
					cy.remove(cy.$id(dataArray[1]));
					console.log(cy.$id(dataArray[1]));
					break;
				case 'operationAndStep':
					operation = dataArray[1];
					operationStep = dataArray[2];
					break;
				case 'fit':
					cy.fit();
					const pan = cy.pan();
					ws.send("fitted;" + pan.x + ";" + pan.y + ";" + cy.zoom());
					break;
			}
			resolve(true);
		});
		await promise;
		processMessage();
	} else {
		messageProcessing = false;
	}
}

function sendSignalRetract(){
	if (graphOperationLogic == "clientSide") handler = new RetractHandler(maxNumberVertices);
	buildTopViewOperations();
	ws.send("buildTopView;retract");
}

function sendSignalAppendJoin(){
	if (graphOperationLogic == "clientSide") handler = new AppendHandler(maxNumberVertices);
	buildTopViewOperations();
	ws.send("buildTopView;appendJoin");
}

function sendSignalAdjacency(){
	if (graphOperationLogic == "clientSide")	handler = new AppendHandler(maxNumberVertices);
	buildTopViewOperations();
	ws.send("buildTopView;adjacency");
}

function buildTopViewOperations(){
	if (!layout) layoutBase = new Set();
	boundingBoxVar = {x1: 0, y1: 0, w: 4000, h: 4000};
	cy.zoom(1 / (4000 / Math.min(cyWidth, cyHeight)));
}

function clientSideLogic(){
	graphOperationLogic = "clientSide";
	ws.send("clientSideLogic");
}

function serverSideLogic(){
	graphOperationLogic = "serverSide";
	ws.send("serverSideLogic");
}

function sendMaxVertices(maxVertices){
	maxNumberVertices = maxVertices;
	console.log("executing sendMaxVertices");
	ws.send("maxVertices;" + maxVertices);
}

function resetVisualization(){
	cy.elements().remove();
	cy.pan({x: 0, y: 0});
	nodeWidth = 50;
	nodeHeight = 50;
	nodeLabelFontSize = 32;
	edgeWidth = 5;
	edgeArrowSize = 2;
	cy.style().selector('node').style({
			'width': nodeWidth,
			'height': nodeHeight,
			'font-size': nodeLabelFontSize
		}).update();
		cy.style().selector('edge').style({
			'width': edgeWidth,
			'arrow-scale': edgeArrowSize
		}).update();
	ws.send("resetWrapperHandler");
}

function preLayout(){
	layout = false;
	ws.send("preLayout");
}

function postLayout(){
	layout = true;
	ws.send("postLayout");
}

let header1 = document.getElementById('header1');
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

$(document).ready(function(){
    ws = new WebSocket("ws://" + jsonObject.ServerIp4 + ":8897/graphData");
	
	ws.onopen = function() {
		console.log("Opened!");
		ws.send("Hello Server");
		resized();
	}

	ws.onmessage = function (evt) {
		console.log(evt.data);
		const dataArray = evt.data.split(";");
		addMessageToQueue(dataArray);
	}
	
	ws.onclose = function() {
		console.log("Closed!");
	};

	ws.onerror = function(err) {
		console.log("Error: " + err);
	};
});

const heightOutput = document.querySelector('#height');
const widthOutput = document.querySelector('#width');

const boundingClientRect = document.getElementById('cy').getBoundingClientRect();
let cyHeight = boundingClientRect.height;
let cyWidth = boundingClientRect.width;
let cyHeightHalf = cyHeight / 2;
let cyWidthHalf = cyWidth / 2;



function resized(){
	const boundingClientRect = document.getElementById('cy').getBoundingClientRect();
	cyHeight = boundingClientRect.height;
	cyWidth = boundingClientRect.width;
	cyHeightHalf = cyHeight / 2;
	cyWidthHalf = cyWidth / 2;
	console.log("resizing after timeout");
	heightOutput.textContent = cyWidth;
	widthOutput.textContent = cyHeight;
	const pan = cy.pan();
	ws.send("viewportSize;" + pan.x + ";" + pan.y + ";" + cy.zoom() + ";" +  cyWidth + ";" + cyHeight);
}


var resizedTimeOut;
window.onresize = function(){
  clearTimeout(resizedTimeOut);
  resizedTimeOut = setTimeout(resized, 100);
};