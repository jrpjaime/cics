package mx.gob.imss.cics.beans;


import com.ibm.ctg.client.Channel;
import com.ibm.ctg.client.exceptions.ChannelException;

public class ECIRequestBean {

	private int callType;
	private String server;
	private String user;
	private String password;
	private String program;
	private String transaction;

	private Channel channel; // El canal que contendrá los contenedores
	private String channelName; // Nombre del canal CICS

	private int modoExtendido;
	private int LuwID;

	public ECIRequestBean(){
		// Inicializar el canal con un nombre por defecto
		this.channelName = "MYCHANNEL";
		try {
			this.channel = new Channel(this.channelName);
		} catch (ChannelException e) {
			System.err.println("Error al crear el Channel '" + this.channelName + "': " + e.getMessage());
			this.channel = null;
		}
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

	/**
	 * Devuelve el objeto Channel. Aquí se añaden los Contenedores.
	 * @return el Channel
	 */
	public Channel getChannel() {
		return channel;
	}

	/**
	 * Establece un Channel preexistente (si se necesita)
	 * @param channel el Channel a establecer
	 */
	public void setChannel(Channel channel) {
		this.channel = channel;
	}

	/**
	 * Devuelve el nombre del Channel.
	 * @return el nombre del canal
	 */
	public String getChannelName() {
		return channelName;
	}

	/**
	 * Establece el nombre del Channel.
	 * @param channelName el nombre del canal a establecer
	 */
	public void setChannelName(String channelName) {
		this.channelName = channelName;
		try {
			this.channel = new Channel(this.channelName); // Regenerar el objeto Channel si se cambia el nombre
		} catch (ChannelException e) {
			System.err.println("Error al re-crear el Channel '" + this.channelName + "': " + e.getMessage());
			this.channel = null;
		}
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
}