package com.xrmaddness.offthegrid;

import java.io.IOException;
import java.io.InputStream;

public class filestring {

	static public String is2str(InputStream is)
	{
		int k;
		StringBuffer sb=new StringBuffer();
	    
		try {
			while((k=is.read())!=-1) {
				sb.append((char)k);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return sb.toString();
	}
}
