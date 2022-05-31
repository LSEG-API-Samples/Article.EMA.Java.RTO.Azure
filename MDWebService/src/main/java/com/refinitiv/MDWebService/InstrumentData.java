package com.refinitiv.MDWebService;

import java.util.*;
import com.refinitiv.ema.access.*;

public class InstrumentData {
	
	public String ric;
	public String dataState = "";
	public HashMap<String, String> dataMap = new HashMap<>();
	
	
	public InstrumentData(String ric)	{
		this.ric = ric;
	}
	
	
	public void setState(OmmState state)	{
		dataState = state.dataStateAsString() + "|" +
			state.statusCodeAsString() + "|" +
			state.statusText() + "|" +
			state.streamStateAsString();
	}


	public void decode(FieldList fieldList)	{
		for(FieldEntry fieldEntry : fieldList)
			dataMap.put(fieldEntry.name(), fieldEntry.load().toString());
	}
	
}
