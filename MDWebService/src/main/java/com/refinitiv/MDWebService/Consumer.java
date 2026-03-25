/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.refinitiv.MDWebService;

import org.springframework.stereotype.*;
import org.springframework.beans.factory.annotation.*;

import java.util.ArrayList;
import java.util.List;

import com.refinitiv.ema.access.*;
import com.refinitiv.ema.access.DataType.DataTypes;
import com.refinitiv.ema.access.OmmConsumerConfig.OperationModel;
import com.refinitiv.ema.rdm.EmaRdm;
import org.slf4j.*;


/**
 * EMA OmmConsumerClient callback handler.
 * Processes market data responses for batch requests and stores
 * the results into the corresponding InstrumentData objects.
 * Each callback resolves its instrument in the Batch closure and
 * calls countDown() so the batch latch knows the item is complete.
 */
class AppClient implements OmmConsumerClient {

	private static final Logger LOG = LoggerFactory.getLogger(AppClient.class);
	// List to store EMA Batch items handles captured from Refresh responses, so they can be unregistered before the next batch request
	private List<Long> handles = new ArrayList<>();

	/** Called when the initial image (snapshot) arrives for a requested item. */
	public void onRefreshMsg(RefreshMsg refreshMsg, OmmConsumerEvent event) {
		try {
			LOG.info("Refresh: " + refreshMsg);
			handles.add(event.handle());
			Batch bRequest = (Batch) event.closure();
			InstrumentData instr = bRequest.getInstrument(refreshMsg.name());
			instr.setState(refreshMsg.state());
			// decode field list payload into the instrument's data map
			if (DataType.DataTypes.FIELD_LIST == refreshMsg.payload().dataType())
				instr.decode(refreshMsg.payload().fieldList());
			bRequest.countDown();
		} catch (Exception e) {
			LOG.error("Exception processing Refresh callback");
			LOG.error("Message: ", refreshMsg);
			LOG.error("Exception: ", e);
		}
	}

	/**
	 * Called when a status-only message arrives (e.g. item not found, stale data).
	 * Records the state on the instrument and signals the batch as done for this item.
	 * Messages without a name (e.g. stream-level status) are intentionally ignored.
	 */
	public void onStatusMsg(StatusMsg statusMsg, OmmConsumerEvent event) {
		if (statusMsg.hasName()) {
			try {
				LOG.info("Status: " + statusMsg);
				Batch bRequest = (Batch) event.closure();
				InstrumentData instr = bRequest.getInstrument(statusMsg.name());
				instr.setState(statusMsg.state());
				bRequest.countDown();
			} catch (Exception e) {
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

	// Update, Generic, Ack, and AllMsg callbacks are not used in snapshot mode
	public void onUpdateMsg(UpdateMsg updateMsg, OmmConsumerEvent event) {
		try{
			LOG.info("Update: " + updateMsg);
		} catch (Exception e) {
			LOG.error("Exception processing Update callback");
			LOG.error("Message: ", updateMsg);
			LOG.error("Exception: ", e);
		}
	}
	public void onGenericMsg(GenericMsg genericMsg, OmmConsumerEvent consumerEvent) {}
	public void onAckMsg(AckMsg ackMsg, OmmConsumerEvent consumerEvent) {}
	public void onAllMsg(Msg msg, OmmConsumerEvent consumerEvent) {}

	/** Returns all EMA stream handles captured from Refresh responses. */
	public List<Long> getHandles() {
		return handles;
	}
}


/**
 * Spring service that manages the EMA OmmConsumer lifecycle.
 * Supports two connection modes (configured via application.properties):
 *   - RTDS: direct connection to a Refinitiv ADS (Advanced Distribution Server)
 *   - RTO:  connection to Refinitiv Real-Time Optimized (cloud), with V1 (Machine-ID)
 *           or V2 (Service Account) authentication
 */
@Service
public class Consumer {

	// --- Connection mode and service settings, set in application.properties ---

	/** "RTDS" for direct ADS connection, "RTO" for cloud connection */
	@Value("${MarketData.ConnectionMode}")
	private String connectionMode;

	/** "V1" (Machine-ID) or "V2" (Service Account) — only used when connectionMode=RTO */
	@Value("${MarketData.RTOAuthenMode}")
	private String rtoAuthenMode;

	/** EMA service name to subscribe to (e.g. ELEKTRON_DD) */
	@Value("${MarketData.ServiceName}") 
	private String serviceName;

	// --- RTDS-only connection settings, set in application.properties ---

	/** ADS hostname — only required when connectionMode=RTDS */
	@Value("${MarketData.Hostname}") 
	private String hostName;

	/** ADS port — only required when connectionMode=RTDS */
	@Value("${MarketData.Port}") 
	private int port;

	/** DACS username for entitlements — only required when connectionMode=RTDS */
	@Value("${MarketData.DACSUsername}")
	private String userName;

	// --- Settings for Streaming or Snapshot mode, set in application.properties ---
	@Value("${MarketData.Streaming}")
	private boolean streaming;

	// --- View / field filtering settings, set in application.properties ---

	/** When true, requests only the field IDs listed in viewFIDS (reduces bandwidth) */
	@Value("${MarketData.ApplyView}")
	private boolean applyView;

	/** Comma-separated list of FIDs to include in view requests */
	@Value("${MarketData.View.FIDS}")
	private int viewFIDS[];

	// --- RTO authentication credentials (read from environment variables for security) ---

	/** V2 Service Account client ID — required when connectionMode=RTO and rtoAuthenMode=V2 */
	@Value("${CLIENT_ID}")
	private String client_id;

	/** V2 Service Account client secret — required when connectionMode=RTO and rtoAuthenMode=V2 */
	@Value("${CLIENT_SECRET}")
	private String client_secret;

	/** V1 Machine-ID username — required when connectionMode=RTO and rtoAuthenMode=V1 */
	@Value("${RTO_MACHINE_ID}")
	private String rto_machine_id;

	/** V1 Machine-ID password — required when connectionMode=RTO and rtoAuthenMode=V1 */
	@Value("${RTO_PASSWORD}")
	private String rto_password;

	/** V1 application key — required when connectionMode=RTO and rtoAuthenMode=V1 */
	@Value("${RTO_APPKEY}")
	private String rto_appkey;

	private OmmConsumer consumer = null;
	private OmmConsumerConfig config = null;
	private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);
	private List<Long> handles = new ArrayList<>();

	AppClient appClient =null;


	/** Throws IllegalArgumentException if the given string is null or blank. */
	private void validateParameter(String param) {
		if ((param == null) || param.trim().isEmpty())
			throw new IllegalArgumentException("Required market data parameter is null or blank");
	}

	/**
	 * Validates configuration, creates the OmmConsumer, and connects to the
	 * market data system. Must be called once at application startup before
	 * any calls to synchronousRequest().
	 */
	public void initialize() {
		// validate required parameters for the chosen connection mode
		if (connectionMode.equals("RTDS")) {
			validateParameter(hostName);
			validateParameter(userName);
		}

		validateParameter(serviceName);

		// create a shared OmmConsumerConfig that will be customized below
		config = EmaFactory.createOmmConsumerConfig();
		// Initialize the AppClient callback handler (defined above) that will process responses for all requests
		appClient = new AppClient();

		if (connectionMode.equals("RTDS")) {
			if (port == 0) {
				throw new IllegalArgumentException("Market data Connection Mode RTDS, ADS port cannot be 0");
			}
		}

		if (applyView && viewFIDS.length == 0)
			throw new IllegalArgumentException("Market data FIDS for VIEW not provided in configuration");

		if (connectionMode.equals("RTDS")) {
			LOG.info("Starting OMMConsumer with following parameters: ");
			LOG.info("ADS: {}:{}, Service: {}, DACS-User: {}, Streaming: {}, View: {},  View-FIDS: {}", hostName, port, serviceName, userName, streaming, applyView, java.util.Arrays.toString(viewFIDS));

			// connect directly to the ADS using hostname/port and DACS username
			consumer = EmaFactory.createOmmConsumer(config.host(hostName + ":" + port).username(userName));
		} else if (connectionMode.equals("RTO")) {
			LOG.info("Starting OMMConsumer connecting to RTO with following parameters: ");
			LOG.info("RTO: Service: {}, Streaming: {}, View: {}, View-FIDS: {}", serviceName, streaming, applyView, java.util.Arrays.toString(viewFIDS));

			// configure credentials based on the RTO authentication mode
			if (rtoAuthenMode.equals("V1")) {
				LOG.info("RTO Authentication Mode: V1 (Machine-ID)");
				// V1 uses a personal Machine-ID, password, and application key
				config.consumerName("Consumer_RTO").username(rto_machine_id).password(rto_password).clientId(rto_appkey);
			} else if (rtoAuthenMode.equals("V2")) {
				LOG.info("RTO Authentication Mode: V2 (Service Account)");
				// V2 uses a service account client ID and secret (OAuth2 client credentials)
				config.consumerName("Consumer_RTO").clientId(client_id).clientSecret(client_secret);
			}

			// connect to RTO using the configured credentials
			consumer = EmaFactory.createOmmConsumer(config);
		}
	}

	/**
	 * Submits a batch snapshot request for all RICs in the given Batch and
	 * blocks until all items have received a Refresh or Status response,
	 * or the batch timeout expires.
	 *
	 * Uses EMA batch (ENAME_BATCH_ITEM_LIST) and optionally a view
	 * (ENAME_VIEW_DATA) to limit the returned fields.
	 * interestAfterRefresh(false) means the subscription is automatically
	 * closed after the image is received (snapshot mode).
	 */
	public void synchronousRequest(Batch bRequest) throws Exception {
		// build the ElementList payload that carries the batch item list (and optional view)
		ElementList eList = EmaFactory.createElementList();
		OmmArray array = EmaFactory.createOmmArray();
		for (String instr : bRequest.getAllRics())
			array.add(EmaFactory.createOmmArrayEntry().ascii(instr));
		eList.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_BATCH_ITEM_LIST, array));

		if (applyView) {
			// add a view to restrict the response to only the configured FIDs
			OmmArray vArray = EmaFactory.createOmmArray();
			for (int fid : viewFIDS)
				vArray.add(EmaFactory.createOmmArrayEntry().intValue(fid));

			eList.add(EmaFactory.createElementEntry().uintValue(EmaRdm.ENAME_VIEW_TYPE, 1));
			eList.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_VIEW_DATA, vArray));
		}

		// Get existing handles from AppClient and unregister them before making a new batch request, to avoid conflicts with previous requests
		handles = appClient.getHandles();
		// Unregister existing streams if any
		if(handles != null && !handles.isEmpty()) {
			for (long handle : handles) {
				consumer.unregister(handle);
			}
			handles.clear();
		}
		// register the batch request; pass the Batch as closure so callbacks can resolve items
		consumer.registerClient(EmaFactory.createReqMsg().serviceName(serviceName).payload(eList).interestAfterRefresh(streaming), appClient, bRequest);

		// block until all items in the batch have been fulfilled (or timeout)
		bRequest.await();
	}

	/** Uninitializes the OmmConsumer and releases all resources. */
	public void disconnect() {
		if (consumer != null)
			consumer.uninitialize();
	}
}