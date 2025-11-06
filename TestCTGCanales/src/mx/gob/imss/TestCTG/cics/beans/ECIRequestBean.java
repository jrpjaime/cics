package mx.gob.imss.TestCTG.cics.beans;

import java.util.Map;
import java.util.HashMap;

public class ECIRequestBean {

	private int callType;
	private String server;
	private String user;
	private String password;
	private String program;
	private String transaction;
	private byte[] commArea;
	private int commAreaLongitud;
	private int modoExtendido;
	private int LuwID;
	// Nuevo campo para Channels and Containers
	private Map<String, byte[]> containers; // Clave: Nombre del Container, Valor: Datos del Container

	public ECIRequestBean() {
		this.containers = new HashMap<>(); // Inicializar el mapa
	}

	public int getCallType() {
		return callType;
	}

	public void setCallType(int callType) {
		this.callType = callType;
	}

	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getProgram() {
		return program;
	}

	public void setProgram(String program) {
		this.program = program;
	}

	public String getTransaction() {
		return transaction;
	}

	public void setTransaction(String transaction) {
		this.transaction = transaction;
	}

	public byte[] getCommArea() {
		return commArea;
	}

	public void setCommArea(byte[] commArea) {
		this.commArea = commArea;
	}

	public int getCommAreaLongitud() {
		return commAreaLongitud;
	}

	public void setCommAreaLongitud(int commAreaLongitud) {
		this.commAreaLongitud = commAreaLongitud;
	}

	public int getModoExtendido() {
		return modoExtendido;
	}

	public void setModoExtendido(int modoExtendido) {
		this.modoExtendido = modoExtendido;
	}

	public int getLuwID() {
		return LuwID;
	}

	public void setLuwID(int luwID) {
		LuwID = luwID;
	}

	// Métodos getter y setter para containers
	public Map<String, byte[]> getContainers() {
		return containers;
	}

	public void setContainers(Map<String, byte[]> containers) {
		this.containers = containers;
	}

	public void addContainer(String name, byte[] data) {
		if (this.containers == null) {
			this.containers = new HashMap<>();
		}
		this.containers.put(name, data);
	}
}