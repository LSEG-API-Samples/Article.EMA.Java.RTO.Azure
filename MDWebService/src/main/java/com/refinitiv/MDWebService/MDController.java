/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2025 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.refinitiv.MDWebService;

import org.slf4j.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.event.*;
import org.springframework.beans.factory.annotation.*;


@RestController
public class MDController {

	private static final Logger LOG = LoggerFactory.getLogger(MDController.class);
	
	@Autowired
	private Consumer ommCons;
	
	@Value("${MarketData.BatchRequestTimeout}") 
	private long timeout;


	@GetMapping("/quotes/{items}")
	@ResponseBody
	public InstrumentData[] getQuote(@PathVariable String[] items) throws Exception {
		LOG.info("Quote request for: {}", java.util.Arrays.toString(items));
		// create a batch request
		Batch btc = new Batch(items, timeout);
		// price it
		ommCons.synchronousRequest(btc);
		// send json array response
		return btc.getAllInstruments();
	}
	
	
	@EventListener
	public void onApplicationEvent(ContextRefreshedEvent event) {
		try {
			LOG.info("Initialize the consumer and connect to market data system....");
			ommCons.initialize();
		} catch (Exception e) {
			LOG.error("Failed to initialize consumer: {}", e.getMessage(), e);
			// You might want to set a flag or take other action to indicate initialization failure
		}
	}	
	
}
