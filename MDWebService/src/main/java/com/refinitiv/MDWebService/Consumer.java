/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2025 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.refinitiv.MDWebService;

import org.springframework.stereotype.*;
import org.springframework.beans.factory.annotation.*;

import com.refinitiv.ema.access.*;
import com.refinitiv.ema.access.DataType.DataTypes;
import com.refinitiv.ema.access.OmmConsumerConfig.OperationModel;
import com.refinitiv.ema.rdm.EmaRdm;
import org.slf4j.*;


class AppClient implements OmmConsumerClient	{

	private static final Logger LOG = LoggerFactory.getLogger(AppClient.class);

	
	public void onRefreshMsg(RefreshMsg refreshMsg, OmmConsumerEvent event)	{
		try	{
			Batch bRequest = (Batch) event.closure();
			InstrumentData instr = bRequest.getInstrument(refreshMsg.name());
			instr.setState(refreshMsg.state());
			if(DataType.DataTypes.FIELD_LIST == refreshMsg.payload().dataType())
				instr.decode(refreshMsg.payload().fieldList());
			bRequest.countDown();
		}
		catch(Exception e)	{
			LOG.error("Exception processing Refresh callback");
			LOG.error("Message: ", refreshMsg);
			LOG.error("Exception: ", e);
		}
	}


	public void onStatusMsg(StatusMsg statusMsg, OmmConsumerEvent event) 	{
		if(statusMsg.hasName())	{
			try	{
				Batch bRequest = (Batch) event.closure();
				InstrumentData instr = bRequest.getInstrument(statusMsg.name());
				instr.setState(statusMsg.state());
				bRequest.countDown();
			}
			catch(Exception e)	{
				LOG.error("Exception processing Status callback");
				LOG.error("Message: ", statusMsg);
				LOG.error("Exception: ", e);
			}
		}
		// else	{
		// 	LOG.info("==== onStatusMsg ====");
		// 	if (statusMsg.hasState())
		// 		LOG.info("Item State: " + statusMsg.state());
		// }
	}

	public void onUpdateMsg(UpdateMsg updateMsg, OmmConsumerEvent event)	{}

	public void onGenericMsg(GenericMsg genericMsg, OmmConsumerEvent consumerEvent) {}

	public void onAckMsg(AckMsg ackMsg, OmmConsumerEvent consumerEvent) {}

	public void onAllMsg(Msg msg, OmmConsumerEvent consumerEvent) {}
}


@Service
public class Consumer 	{

	// market data configuration values read from application.properties

	@Value("${MarketData.ConnectionMode}")
	private String connectionMode;

	@Value("${MarketData.ServiceName}") 
	private String serviceName;

	@Value("${MarketData.Hostname}") 
	private String hostName;

	@Value("${MarketData.Port}") 
	private int port;

	@Value("${MarketData.DACSUsername}")
	private String userName;

	@Value("${MarketData.ApplyView}")
	private boolean applyView;
	
	@Value("${MarketData.View.FIDS}")
	private int viewFIDS[];

	@Value("${CLIENT_ID}")
	private String client_id;

	@Value("${CLIENT_SECRET}")
	private String client_secret;

	private OmmConsumer consumer = null;
	private static final Logger LOG = LoggerFactory.getLogger(AppClient.class);



	private void validateParameter(String param)	{
		if((param == null) || param.trim().isEmpty())
			throw new IllegalArgumentException("Required market data parameter is null or blank");
	}



	public void initialize()	{
		// validate the market data properties
		if (connectionMode.equals("RTDS")){
			validateParameter(hostName);
			validateParameter(userName);
		}
		
		validateParameter(serviceName);

		//LOG.info("Hello, CLIENT_ID {}", client_id);
		//LOG.info("Hello, CLIENT_SECRET {}", client_secret);
		
		if (connectionMode.equals("RTDS")){
			if(port == 0){
				throw new IllegalArgumentException("Market data Connection Mode RTDS, ADS port cannot be 0");
			}
		}
			
		if(applyView && viewFIDS.length == 0)
			throw new IllegalArgumentException("Market data FIDS for VIEW not provided in configuration");

		if (connectionMode.equals("RTDS")){
			LOG.info("Starting OMMConsumer with following parameters: ");
			LOG.info("ADS: {}:{}, Service: {}, DACS-User: {}, View: {}, View-FIDS: {}", hostName, port, serviceName, userName, applyView, java.util.Arrays.toString(viewFIDS));

			// initialize the OMM consumer to RTDS
			consumer  = EmaFactory.createOmmConsumer(EmaFactory.createOmmConsumerConfig()
			.host(hostName + ":" + port)
			.username(userName));
		} else if (connectionMode.equals("RTO")){
			LOG.info("Starting OMMConsumer connecting to RTO with following parameters: ");
			LOG.info("RTO: Service: {},  View: {}, View-FIDS: {}",  serviceName, applyView, java.util.Arrays.toString(viewFIDS));

			// initialize the OMM consumer to RTO
			consumer  = EmaFactory.createOmmConsumer(EmaFactory.createOmmConsumerConfig()
			.consumerName("Consumer_RTO")
			.clientId(client_id)
			.clientSecret(client_secret));
		}
	}

	public void synchronousRequest(Batch bRequest) throws Exception	{
		ElementList eList = EmaFactory.createElementList();
		OmmArray array = EmaFactory.createOmmArray();
		for(String instr : bRequest.getAllRics())
			array.add(EmaFactory.createOmmArrayEntry().ascii(instr));
		eList.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_BATCH_ITEM_LIST, array));
		
		if(applyView)	{
			OmmArray vArray = EmaFactory.createOmmArray();
			for(int fid : viewFIDS)
				vArray.add(EmaFactory.createOmmArrayEntry().intValue(fid));
		
			eList.add(EmaFactory.createElementEntry().uintValue(EmaRdm.ENAME_VIEW_TYPE, 1));
			eList.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_VIEW_DATA, vArray));
		}		
		
		consumer.registerClient(EmaFactory.createReqMsg().serviceName(serviceName).payload(eList).interestAfterRefresh(false), new AppClient(), bRequest);
		
		// wait for batch to be fulfilled
		bRequest.await();
	}
	
	
	public void disconnect()	{
		if (consumer != null) 
			consumer.uninitialize();
	}
	
}
