package mx.gob.imss.cics.beans;


import com.ibm.ctg.client.Channel; 

import lombok.Data;

@Data
public class ECIRequestBean {

	private int callType;
	private String server;
	private String user;
	private String password;
	private String program;
	private String transaction;

	private Channel channel; // El canal que contendrá los contenedores
	private String channelName = "MYCHANNEL";

	private int modoExtendido;
	private int LuwID;

 

	public ECIRequestBean(){
	 
	}

 
}