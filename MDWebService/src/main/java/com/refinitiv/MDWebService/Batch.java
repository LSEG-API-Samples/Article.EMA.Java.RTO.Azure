package com.refinitiv.MDWebService;

import java.util.*;
import java.util.concurrent.*;


public class Batch	{
	private static int id = 0;
	
	private int thisID = 0;
	private long _startTimeStamp = 0, _endTimeStamp = 0;
	private Map<String, InstrumentData> batchInstruments;
	private CountDownLatch countDownLatch;
	private long timeout = 0;

	Batch(String rics[], long timeout)	{
		thisID = id++;
		this.timeout = timeout;
		batchInstruments = new HashMap<String, InstrumentData>(rics.length);
		for(String ric : rics)
			batchInstruments.put(ric, new InstrumentData(ric));
		
		countDownLatch = new CountDownLatch(batchInstruments.size());
	}


	public String[] getAllRics()	{
		return batchInstruments.keySet().toArray(String[]::new);
	}


	public InstrumentData getInstrument(String ric) throws Exception	 {
		if(!batchInstruments.containsKey(ric))
			throw new Exception("RIC not found in batch: " + ric);

		return batchInstruments.get(ric);
	}


	public InstrumentData[] getAllInstruments()	 {
		return batchInstruments.values().toArray(new InstrumentData[0]);
	}


	public void recordStartTime()	{
		_startTimeStamp = System.currentTimeMillis();
		_endTimeStamp  = 0;
	}

	public void recordEndTime()	{
		_endTimeStamp = System.currentTimeMillis();
	}

	public boolean await() throws Exception {
		return countDownLatch.await(timeout, TimeUnit.MILLISECONDS);
	}

	public void countDown() {
		countDownLatch.countDown();
	}
	
	public long batchFulfilmentTime()	{
		return _endTimeStamp - _startTimeStamp;
	}
	
	public int getBatchId()	{
		return thisID;
	}
	
}
