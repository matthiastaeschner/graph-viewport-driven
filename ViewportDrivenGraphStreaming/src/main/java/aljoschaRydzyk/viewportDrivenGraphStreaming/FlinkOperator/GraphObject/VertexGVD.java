package aljoschaRydzyk.viewportDrivenGraphStreaming.FlinkOperator.GraphObject;

import java.io.Serializable;

public class VertexGVD implements Serializable{
	private String idGradoop;
	private String label;
	private int x;
	private int y;
	private long idNumeric;
	private long degree;
	
	public VertexGVD(String idGradoop, String label, long idNumeric, int x, int y, long degree) {
		this.idGradoop = idGradoop;
		this.label = label;
		this.x = x;
		this.y = y;
		this.idNumeric = idNumeric;
		this.degree = degree;
	}
	
	public VertexGVD(String idGradoop, String label, long idNumeric, long degree) {
		this.idGradoop = idGradoop;
		this.label = label;
		this.idNumeric = idNumeric;
		this.degree = degree;
	}
	
	public VertexGVD(String idGradoop, int x, int y) {
		this.idGradoop = idGradoop;
		this.x = x;
		this.y = y;
	}
	
	public String getIdGradoop() {
		return this.idGradoop;
	}
	
	public String getLabel() {
		return this.label;
	}
	
	public int getX() {
		return this.x;
	}
	
	public int getY() {
		return this.y;
	}
	
	public long getIdNumeric() {
		return this.idNumeric;
	}
	
	public Long getDegree() {
		return this.degree;
	}
	
	public void setIdGradoop(String idGradoop) {
		this.idGradoop = idGradoop;
	}
	
	public void setLabel(String label) {
		this.label = label;
	}
	
	public void setX(int x) {
		this.x = x;
	}
	
	public void setY(int y) {
		this.y = y;
	}
	
	public void setIdNumeric(long idNumeric) {
		this.idNumeric = idNumeric;
	}
	
	public void setDegree(Long degree) {
		this.degree = degree;
	}
}
